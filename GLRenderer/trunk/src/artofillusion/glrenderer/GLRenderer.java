/* Copyright (C) 2001-2008 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.glrenderer;

import artofillusion.*;
import artofillusion.Renderer;
import artofillusion.image.*;
import artofillusion.math.*;
import artofillusion.object.*;
import artofillusion.texture.*;
import artofillusion.ui.*;
import buoy.event.*;
import buoy.widget.*;

import javax.media.opengl.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.nio.*;
import java.io.*;

import com.sun.opengl.util.*;

/** Raster is a Renderer which generates images with a scanline algorithm. */

public class GLRenderer implements Renderer, Runnable
{
  private FormContainer configPanel;
  private BCheckBox transparentBox, adaptiveBox, hideBackfaceBox, hdrBox;
  private BComboBox shadeChoice, aliasChoice, sampleChoice;
  private ValueField errorField, smoothField;
  private int width, height, envMode;
  private long updateTime;
  private Scene theScene;
  private Camera theCamera;
  private RenderListener listener;
  private BufferedImage image;
  private Thread renderThread;
  private double time, smoothing = 1.0, smoothScale, depthOfField, focalDist, surfaceError = 0.02, fogDist;
  private boolean fog, transparentBackground = false, adaptive = true, hideBackfaces = true, generateHDR = false, positionNeeded, depthNeeded, needCopyToUI = true;
  private FloatBuffer vertBuffer, normBuffer;
  private int diffuseColorId, hilightColorId, emissiveColorId, roughnessId;
  private boolean cullingEnabled;
  private TextureSpec spec;

  public GLRenderer()
  {
  }

  /* Methods from the Renderer interface. */

  public String getName()
  {
    return "OpenGL Renderer";
  }

  public synchronized void renderScene(Scene theScene, Camera camera, RenderListener rl, SceneCamera sceneCamera)
  {
    Dimension dim = camera.getSize();

    listener = rl;
    this.theScene = theScene;
    theCamera = camera.duplicate();
    if (sceneCamera == null)
    {
      depthOfField = 0.0;
      focalDist = theCamera.getDistToScreen();
      depthNeeded = false;
    }
    else
    {
      depthOfField = sceneCamera.getDepthOfField();
      focalDist = sceneCamera.getFocalDistance();
      depthNeeded = ((sceneCamera.getComponentsForFilters()&ComplexImage.DEPTH) != 0);
    }
    time = theScene.getTime();
    width = dim.width;
    height = dim.height;
    theCamera.setSize(width, height);
    theCamera.setDistToScreen(theCamera.getDistToScreen());
    theCamera.setClipDistance(theCamera.getClipDistance());
    spec = new TextureSpec();
    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    renderThread = new Thread(this, "OpenGL Renderer Main Thread");
    renderThread.start();
  }

  public synchronized void cancelRendering(Scene sc)
  {
    Thread t = renderThread;
    RenderListener rl = listener;

    if (theScene != sc)
      return;
    renderThread = null;
    if (t == null)
      return;
    try
    {
      while (t.isAlive())
      {
        Thread.sleep(100);
      }
    }
    catch (InterruptedException ex)
    {
    }
    finish(null);
    rl.renderingCanceled();
  }

  public Widget getConfigPanel()
  {
    if (configPanel == null)
    {
      configPanel = new FormContainer(3, 5);
      LayoutInfo leftLayout = new LayoutInfo(LayoutInfo.EAST, LayoutInfo.NONE, new Insets(0, 0, 0, 5), null);
      LayoutInfo rightLayout = new LayoutInfo(LayoutInfo.WEST, LayoutInfo.NONE, null, null);
      configPanel.add(Translate.label("surfaceAccuracy"), 0, 0, leftLayout);
      configPanel.add(Translate.label("supersampling"), 0, 2, leftLayout);
      configPanel.add(errorField = new ValueField(surfaceError, ValueField.POSITIVE, 6), 1, 0, rightLayout);
      configPanel.add(aliasChoice = new BComboBox(new String [] {
        Translate.text("none"),
        Translate.text("Edges"),
        Translate.text("Everything")
      }), 1, 2, rightLayout);
      configPanel.add(sampleChoice = new BComboBox(new String [] {"2x2", "3x3"}), 2, 2, rightLayout);
      sampleChoice.setEnabled(false);
      configPanel.add(transparentBox = new BCheckBox(Translate.text("transparentBackground"), transparentBackground), 0, 3, 3, 1);
      configPanel.add(Translate.button("advanced", this, "showAdvancedWindow"), 0, 4, 3, 1);
      smoothField = new ValueField(smoothing, ValueField.NONNEGATIVE);
      adaptiveBox = new BCheckBox(Translate.text("reduceAccuracyForDistant"), adaptive);
      hideBackfaceBox = new BCheckBox(Translate.text("eliminateBackfaces"), hideBackfaces);
      hdrBox = new BCheckBox(Translate.text("generateHDR"), generateHDR);
      aliasChoice.addEventLink(ValueChangedEvent.class, new Object() {
        void processEvent()
        {
          sampleChoice.setEnabled(aliasChoice.getSelectedIndex() > 0);
        }
      });
    }
    if (needCopyToUI)
      copyConfigurationToUI();
    return configPanel;
  }

  private void showAdvancedWindow(WidgetEvent ev)
  {
    // Record the current settings.

    smoothing = smoothField.getValue();
    adaptive = adaptiveBox.getState();
    hideBackfaces = hideBackfaceBox.getState();
    generateHDR = hdrBox.getState();

    // Show the window.

    WindowWidget parent = UIUtilities.findWindow(ev.getWidget());
    ComponentsDialog dlg  = new ComponentsDialog(parent, Translate.text("advancedOptions"),
          new Widget [] {smoothField, adaptiveBox, hideBackfaceBox, hdrBox},
          new String [] {Translate.text("texSmoothing"), null, null, null});
    if (!dlg.clickedOk())
    {
      // Reset the components.

      smoothField.setValue(smoothing);
      adaptiveBox.setState(adaptive);
      hideBackfaceBox.setState(hideBackfaces);
      hdrBox.setState(generateHDR);
    }
  }

  /** Copy the current configuration to the user interface. */

  private void copyConfigurationToUI()
  {
    needCopyToUI = false;
    if (configPanel == null)
      getConfigPanel();
    smoothField.setValue(smoothing);
    adaptiveBox.setState(adaptive);
    hideBackfaceBox.setState(hideBackfaces);
    hdrBox.setState(generateHDR);
    errorField.setValue(surfaceError);
    transparentBox.setState(transparentBackground);
  }

  public boolean recordConfiguration()
  {
    smoothing = smoothField.getValue();
    adaptive = adaptiveBox.getState();
    hideBackfaces = hideBackfaceBox.getState();
    generateHDR = hdrBox.getState();
    surfaceError = errorField.getValue();
    transparentBackground = transparentBox.getState();
    return true;
  }


  public Map<String, Object> getConfiguration()
  {
    HashMap<String, Object> map = new HashMap<String, Object>();
    map.put("textureSmoothing", new Double(smoothing));
    map.put("reduceAccuracyForDistant", new Boolean(adaptive));
    map.put("hideBackfaces", new Boolean(hideBackfaces));
    map.put("highDynamicRange", Boolean.valueOf(generateHDR));
    map.put("maxSurfaceError", new Double(surfaceError));
    map.put("transparentBackground", new Boolean(transparentBackground));
    return map;
  }

  public void setConfiguration(String property, Object value)
  {
    needCopyToUI = true;
    if ("textureSmoothing".equals(property))
      smoothing = ((Number) value).doubleValue();
    else if ("reduceAccuracyForDistant".equals(property))
      adaptive = ((Boolean) value).booleanValue();
    else if ("hideBackfaces".equals(property))
      hideBackfaces = ((Boolean) value).booleanValue();
    else if ("highDynamicRange".equals(property))
      generateHDR = ((Boolean) value).booleanValue();
    else if ("maxSurfaceError".equals(property))
      surfaceError = ((Number) value).doubleValue();
    else if ("transparentBackground".equals(property))
      transparentBackground = ((Boolean) value).booleanValue();
  }

  public void configurePreview()
  {
    if (needCopyToUI)
      copyConfigurationToUI();
    transparentBackground = false;
    smoothing = 1.0;
    adaptive = hideBackfaces = true;
    generateHDR = false;
    surfaceError = 0.02;
  }

  /** Main method in which the image is rendered. */

  public void run()
  {
    final Thread thisThread = Thread.currentThread();
    if (renderThread != thisThread)
      return;
    updateTime = System.currentTimeMillis();

    // Record information about the scene.

    envMode = theScene.getEnvironmentMode();
    fog = theScene.getFogState();
    fogDist = theScene.getFogDistance();

    GLPbuffer pbuffer = GLDrawableFactory.getFactory().createGLPbuffer(new GLCapabilities(), new DefaultGLCapabilitiesChooser(), 1, 1, null);
    pbuffer.addGLEventListener(new CanvasListener());
    pbuffer.display();

    // Render the objects.

    pbuffer.destroy();
    finish(new ComplexImage(image));
  }

  /**
   * Sort the objects in the scene into the most efficient order for rendering.
   */

  private ObjectInfo[] sortObjects()
  {
    class SortRecord implements Comparable
    {
      public ObjectInfo object;
      public double depth;
      public boolean isTransparent;

      SortRecord(ObjectInfo object)
      {
        this.object = object;
        depth = theCamera.getObjectToView().times(object.getBounds().getCenter()).z;
        if (object.object.getTexture() != null)
          isTransparent = (object.object.getTexture().hasComponent(Texture.TRANSPARENT_COLOR_COMPONENT));
      }

      public int compareTo(Object o)
      {
        SortRecord other = (SortRecord) o;
        if (isTransparent == other.isTransparent)
        {
          // Order by depth.

          if (depth < other.depth)
            return -1;
          if (depth == other.depth)
            return 0;
          return 1;
        }

        // Put transparent objects last.

        if (isTransparent)
          return 1;
        return -1;
      }
    }
    ArrayList<SortRecord> objects = new ArrayList<SortRecord>();
    for (int i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo obj = theScene.getObject(i);
      theCamera.setObjectTransform(obj.coords.fromLocal());
      objects.add(new SortRecord(obj));
    }
    Collections.sort(objects);
    ObjectInfo result[] = new ObjectInfo[objects.size()];
    for (int i = 0; i < result.length; i++)
      result[i] = objects.get(i).object;
    return result;
  }

  /** Create a BufferedImage from the OpenGL frame buffer. */

  private void createImage(GL gl)
  {
    ByteBuffer data = BufferUtil.newByteBuffer(width*height*3);
    gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
    gl.glReadPixels(0, 0, width, height, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, data);
    int pixel[] = ((DataBufferInt) ((BufferedImage) image).getRaster().getDataBuffer()).getData();
    for (int row = 0; row < height; row++)
      for (int col = 0; col < width; col++)
      {
        int argb = 0xFF000000 + ((data.get()&0xFF)<<16) + ((data.get()&0xFF)<<8) + (data.get()&0xFF);
        pixel[width*(height-row-1)+col] = argb;
      }
  }

  /** Update the image being displayed. */

  private synchronized void updateImage()
  {
    if (System.currentTimeMillis()-updateTime < 5000)
      return;
    listener.imageUpdated(image);
    updateTime = System.currentTimeMillis();
  }

  /** This routine is called when rendering is finished. */

  private void finish(ComplexImage finalImage)
  {
    theScene = null;
    theCamera = null;
    image = null;
    spec = null;
    vertBuffer = null;
    normBuffer = null;
    RenderListener rl = listener;
    listener = null;
    renderThread = null;
    if (rl != null && finalImage != null)
      rl.imageComplete(finalImage);
  }

  /** Estimate the range of depth values that the camera will need to render.  This need not be exact,
      but should err on the side of returning bounds that are slightly too large.
      @return the two element array {minDepth, maxDepth}
   */

  private double[] estimateDepthRange()
  {
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    Mat4 toView = theCamera.getWorldToView();
    for (int i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo info = theScene.getObject(i);
      BoundingBox bounds = info.getBounds();
      double dx = bounds.maxx-bounds.minx;
      double dy = bounds.maxy-bounds.miny;
      double dz = bounds.maxz-bounds.minz;
      double size = 0.5*Math.sqrt(dx*dx+dy*dy+dz*dz);
      double depth = toView.times(info.coords.getOrigin()).z;
      if (depth-size < min)
        min = depth-size;
      if (depth+size > max)
        max = depth+size;
    }
    return new double [] {min, max};
  }

  /** Prepare for drawing with or without backface culling. */

  private void prepareCulling(GL gl, boolean cullBackfaces)
  {
    if (cullingEnabled == cullBackfaces)
      return;
    cullingEnabled = cullBackfaces;
    if (cullingEnabled)
      gl.glEnable(GL.GL_CULL_FACE);
    else
      gl.glDisable(GL.GL_CULL_FACE);
  }

  /** Prepare the buffers used for storing vertex and normal arrays. */

  private void prepareBuffers(GL gl, int requiredSize)
  {
    if (vertBuffer == null || vertBuffer.capacity() < requiredSize)
    {
      vertBuffer = BufferUtil.newFloatBuffer(requiredSize);
      normBuffer = BufferUtil.newFloatBuffer(requiredSize);
      gl.glVertexPointer(3, GL.GL_FLOAT, 0, vertBuffer);
      gl.glNormalPointer(GL.GL_FLOAT, 0, normBuffer);
    }
  }

  /** Render an object to the image. */

  private void renderObject(GL gl, ObjectInfo info)
  {
    if (!info.visible)
      return;
    if (theCamera.visibility(info.getBounds()) == Camera.NOT_VISIBLE)
      return;
//    Thread currentThread = Thread.currentThread();
//    if (currentThread != renderThread)
//      return;
    if (info.object instanceof ObjectCollection)
      {
        Enumeration objects = ((ObjectCollection) info.object).getObjects(info, false, theScene);
        while (objects.hasMoreElements())
          {
            ObjectInfo elem = (ObjectInfo) objects.nextElement();
            ObjectInfo copy = elem.duplicate();
            copy.coords.transformCoordinates(info.coords.fromLocal());
            renderObject(gl, copy);
          }
        return;
      }
    double tol;
    if (adaptive)
      {
        double dist = info.getBounds().distanceToPoint(info.coords.toLocal().times(theCamera.getCameraCoordinates().getOrigin()));
        double distToScreen = theCamera.getDistToScreen();
        if (dist < distToScreen)
          tol = surfaceError;
        else
          tol = surfaceError*dist/distToScreen;
      }
    else
      tol = surfaceError;
    RenderingMesh mesh = info.getRenderingMesh(tol);
    if (mesh == null)
      return;
//    if (currentThread != renderThread)
//      return;
    theCamera.setObjectTransform(info.coords.fromLocal());
    Mat4 toView = theCamera.getObjectToView();
    gl.glMatrixMode(GL.GL_MODELVIEW);
    gl.glLoadMatrixd(new double [] {
        -toView.m11, toView.m21, -toView.m31, toView.m41,
        -toView.m12, toView.m22, -toView.m32, toView.m42,
        -toView.m13, toView.m23, -toView.m33, toView.m43,
        -toView.m14, toView.m24, -toView.m34, toView.m44
    }, 0);
    prepareCulling(gl, hideBackfaces && info.object.isClosed() && !info.object.getTexture().hasComponent(Texture.TRANSPARENT_COLOR_COMPONENT));

    // Set up the material properties to let OpenGL shade the surface.

    info.object.getTexture().getAverageSpec(spec, time, info.object.getAverageParameterValues());
    gl.glUniform3f(diffuseColorId, spec.diffuse.getRed(), spec.diffuse.getGreen(), spec.diffuse.getBlue());
    gl.glUniform3f(hilightColorId, spec.hilight.getRed(), spec.hilight.getGreen(), spec.hilight.getBlue());
    gl.glUniform3f(emissiveColorId, spec.emissive.getRed(), spec.emissive.getGreen(), spec.emissive.getBlue());
    gl.glUniform1f(roughnessId, (float) spec.roughness);

    // Fill in buffers with the vertices and normals.

    prepareBuffers(gl, mesh.triangle.length*9);
    vertBuffer.clear();
    normBuffer.clear();
    int faceCount = 0;
    for (RenderingTriangle tri : mesh.triangle)
    {
      faceCount++;
      Vec3 v = mesh.vert[tri.v1];
      vertBuffer.put((float) v.x);
      vertBuffer.put((float) v.y);
      vertBuffer.put((float) v.z);
      v = mesh.vert[tri.v2];
      vertBuffer.put((float) v.x);
      vertBuffer.put((float) v.y);
      vertBuffer.put((float) v.z);
      v = mesh.vert[tri.v3];
      vertBuffer.put((float) v.x);
      vertBuffer.put((float) v.y);
      vertBuffer.put((float) v.z);

      // Set the normals from the mesh.

      v = mesh.norm[tri.n1];
      normBuffer.put((float) v.x);
      normBuffer.put((float) v.y);
      normBuffer.put((float) v.z);
      v = mesh.norm[tri.n2];
      normBuffer.put((float) v.x);
      normBuffer.put((float) v.y);
      normBuffer.put((float) v.z);
      v = mesh.norm[tri.n3];
      normBuffer.put((float) v.x);
      normBuffer.put((float) v.y);
      normBuffer.put((float) v.z);
    }
    gl.glDrawArrays(GL.GL_TRIANGLES, 0, faceCount*3);
  }

  private String readFile(String name) throws IOException
  {
    BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(name)));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = in.readLine()) != null)
      sb.append(line).append('\n');
    return sb.toString();
  }

  private int createShader(GL gl, int type, String source) throws GLException
  {
    int shader = gl.glCreateShader(type);
    gl.glShaderSource(shader, 1, new String[] {source}, new int[] {source.length()}, 0);
    gl.glCompileShader(shader);
    int status[] = new int[1];
    gl.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0);
    if (status[0] != GL.GL_TRUE)
      throw new GLException("Shader failed to compile: "+source);
    return shader;
  }

  /** Create the shader which performs lighting calculations. */

  private String createLightingShader() throws IOException
  {
    ArrayList<ObjectInfo> directionalLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientDirectionalLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> pointLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientPointLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> spotLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientSpotLights = new ArrayList<ObjectInfo>();

    for (int i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo info = theScene.getObject(i);
      if (!(info.object instanceof Light))
        continue;
      if (!info.visible)
        continue;
      Light light = (Light) info.object;
      if (light instanceof DirectionalLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientDirectionalLights.add(info);
        else
          directionalLights.add(info);
      }
      if (light instanceof PointLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientPointLights.add(info);
        else
          pointLights.add(info);
      }
      if (light instanceof SpotLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientSpotLights.add(info);
        else
          spotLights.add(info);
      }
    }
    StringBuilder shader = new StringBuilder(readFile("artofillusion/glrenderer/shaderMainStart.txt"));
    RGBColor ambient = theScene.getAmbientColor().duplicate();
    for (ObjectInfo info : ambientDirectionalLights)
    {
      RGBColor light = new RGBColor();
      ((DirectionalLight) info.object).getLight(light, 0.0f);
      ambient.add(light);
    }
    shader.append("vec3 diffuseLight = vec3(").append(ambient.getRed()).append(", ").append(ambient.getGreen()).append(", ").append(ambient.getBlue()).append(");\n");
    Mat4 toView = theCamera.getWorldToView();
    toView = new Mat4(-toView.m11, -toView.m12, -toView.m13, -toView.m14,
                      toView.m21, toView.m22, toView.m23, toView.m24,
                      -toView.m31, -toView.m32, -toView.m33, -toView.m34,
                      toView.m41, toView.m42, toView.m43, toView.m44
    );
    if (pointLights.size() > 0)
    {
      shader.append("const int pointLightCount = ").append(pointLights.size()).append(";\n");
      shader.append("vec3 pointLightPos[").append(pointLights.size()).append("];\n");
      shader.append("vec3 pointLightColor[").append(pointLights.size()).append("];\n");
      shader.append("float pointLightDecayRate[").append(pointLights.size()).append("];\n");
      for (int i = 0; i < pointLights.size(); i++)
      {
        PointLight light = (PointLight) pointLights.get(i).object;
        Vec3 pos = toView.times(pointLights.get(i).coords.getOrigin());
        shader.append("pointLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("pointLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        shader.append("pointLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/pointLight.txt"));
    }
    if (ambientPointLights.size() > 0)
    {
      shader.append("const int ambientPointLightCount = ").append(ambientPointLights.size()).append(";\n");
      shader.append("vec3 ambientPointLightPos[").append(ambientPointLights.size()).append("];\n");
      shader.append("vec3 ambientPointLightColor[").append(ambientPointLights.size()).append("];\n");
      shader.append("float ambientPointLightDecayRate[").append(ambientPointLights.size()).append("];\n");
      for (int i = 0; i < ambientPointLights.size(); i++)
      {
        PointLight light = (PointLight) ambientPointLights.get(i).object;
        Vec3 pos = toView.times(ambientPointLights.get(i).coords.getOrigin());
        shader.append("ambientPointLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("ambientPointLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        shader.append("ambientPointLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/ambientPointLight.txt"));
    }
    if (directionalLights.size() > 0)
    {
      shader.append("const int directionalLightCount = ").append(directionalLights.size()).append(";\n");
      shader.append("vec3 directionalLightDir[").append(directionalLights.size()).append("];\n");
      shader.append("vec3 directionalLightHilightDir[").append(directionalLights.size()).append("];\n");
      shader.append("vec3 directionalLightColor[").append(directionalLights.size()).append("];\n");
      for (int i = 0; i < directionalLights.size(); i++)
      {
        DirectionalLight light = (DirectionalLight) directionalLights.get(i).object;
        Vec3 dir = toView.timesDirection(directionalLights.get(i).coords.getZDirection());
        shader.append("directionalLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        dir.z -= 1.0;
        dir.normalize();
        shader.append("directionalLightHilightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("directionalLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/directionalLight.txt"));
    }
    if (spotLights.size() > 0)
    {
      shader.append("const int spotLightCount = ").append(spotLights.size()).append(";\n");
      shader.append("vec3 spotLightPos[").append(spotLights.size()).append("];\n");
      shader.append("vec3 spotLightColor[").append(spotLights.size()).append("];\n");
      shader.append("vec3 spotLightDir[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightDecayRate[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightCutoff[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightExponent[").append(spotLights.size()).append("];\n");
      for (int i = 0; i < spotLights.size(); i++)
      {
        SpotLight light = (SpotLight) spotLights.get(i).object;
        Vec3 pos = toView.times(spotLights.get(i).coords.getOrigin());
        shader.append("spotLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("spotLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        Vec3 dir = toView.timesDirection(spotLights.get(i).coords.getZDirection());
        shader.append("spotLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        shader.append("spotLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
        shader.append("spotLightCutoff[").append(i).append("] = ")
          .append(light.getAngleCosine()).append(";\n");
        shader.append("spotLightExponent[").append(i).append("] = ")
          .append(light.getExponent()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/spotLight.txt"));
    }
    if (ambientSpotLights.size() > 0)
    {
      shader.append("const int ambientSpotLightCount = ").append(ambientSpotLights.size()).append(";\n");
      shader.append("vec3 ambientSpotLightPos[").append(ambientSpotLights.size()).append("];\n");
      shader.append("vec3 ambientSpotLightColor[").append(ambientSpotLights.size()).append("];\n");
      shader.append("vec3 ambientSpotLightDir[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightDecayRate[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightCutoff[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightExponent[").append(ambientSpotLights.size()).append("];\n");
      for (int i = 0; i < ambientSpotLights.size(); i++)
      {
        SpotLight light = (SpotLight) ambientSpotLights.get(i).object;
        Vec3 pos = toView.times(ambientSpotLights.get(i).coords.getOrigin());
        shader.append("ambientSpotLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("ambientSpotLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        Vec3 dir = toView.timesDirection(ambientSpotLights.get(i).coords.getZDirection());
        shader.append("ambientSpotLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        shader.append("ambientSpotLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
        shader.append("ambientSpotLightCutoff[").append(i).append("] = ")
          .append(light.getAngleCosine()).append(";\n");
        shader.append("ambientSpotLightExponent[").append(i).append("] = ")
          .append(light.getExponent()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/ambientSpotLight.txt"));
    }
    shader.append(readFile("artofillusion/glrenderer/shaderMainEnd.txt"));
//System.out.println(shader.toString());
    return shader.toString();
  }

  /** This inner class implements the callbacks to perform drawing with Jogl. */

  private class CanvasListener implements GLEventListener
  {
    public void init(GLAutoDrawable drawable)
    {
      GL gl = drawable.getGL();

      // Create a Frame Buffer Object in which to do the rendering.

      int fbo[] = new int[1];
      gl.glGenFramebuffersEXT(1, fbo, 0);
      gl.glBindFramebufferEXT(GL.GL_FRAMEBUFFER_EXT, fbo[0]);
      int renderBuffer[] = new int[1];
      gl.glGenRenderbuffersEXT(1, renderBuffer, 0);
      gl.glBindRenderbufferEXT(GL.GL_RENDERBUFFER_EXT, renderBuffer[0]);
      gl.glRenderbufferStorageEXT(GL.GL_RENDERBUFFER_EXT, GL.GL_RGB, width, height);
      gl.glFramebufferRenderbufferEXT(GL.GL_FRAMEBUFFER_EXT, GL.GL_COLOR_ATTACHMENT0_EXT, GL.GL_RENDERBUFFER_EXT, renderBuffer[0]);
      int depthBuffer[] = new int[1];
      gl.glGenRenderbuffersEXT(1, depthBuffer, 0);
      gl.glBindRenderbufferEXT(GL.GL_RENDERBUFFER_EXT, depthBuffer[0]);
      gl.glRenderbufferStorageEXT(GL.GL_RENDERBUFFER_EXT, GL.GL_DEPTH_COMPONENT, width, height);
      gl.glFramebufferRenderbufferEXT(GL.GL_FRAMEBUFFER_EXT, GL.GL_DEPTH_ATTACHMENT_EXT, GL.GL_RENDERBUFFER_EXT, depthBuffer[0]);
      gl.glBindFramebufferEXT(GL.GL_FRAMEBUFFER_EXT, fbo[0]);
      gl.glViewport(0, 0, width, height);

      gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
      gl.glEnableClientState(GL.GL_NORMAL_ARRAY);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
    {
    }

    public void display(GLAutoDrawable drawable)
    {
      GL gl = drawable.getGL();
      updateTime = System.currentTimeMillis();

      try
      {
        int vertShader = createShader(gl, GL.GL_VERTEX_SHADER, readFile("artofillusion/glrenderer/vertexShader.txt"));
        int fragShader = createShader(gl, GL.GL_FRAGMENT_SHADER, createLightingShader());
        int program = gl.glCreateProgram();
        gl.glAttachShader(program, vertShader);
        gl.glAttachShader(program, fragShader);
        gl.glLinkProgram(program);
        int status[] = new int[1];
        gl.glGetProgramiv(program, GL.GL_LINK_STATUS, status, 0);
        System.out.println(status[0]);
        gl.glUseProgram(program);

        diffuseColorId = gl.glGetUniformLocation(program, "diffuseColor");
        hilightColorId = gl.glGetUniformLocation(program, "hilightColor");
        emissiveColorId = gl.glGetUniformLocation(program, "emissiveColor");
        roughnessId = gl.glGetUniformLocation(program, "roughness");
      }
      catch (Exception ex)
      {
        ex.printStackTrace();
      }

      Color background = theScene.getEnvironmentColor().getColor();
      gl.glClearColor(background.getRed()/255.0f, background.getGreen()/255.0f, background.getBlue()/255.0f, 0.0f);
      gl.glClear(GL.GL_COLOR_BUFFER_BIT+GL.GL_DEPTH_BUFFER_BIT);
      gl.glEnable(GL.GL_DEPTH_TEST);
      double depthRange[] = estimateDepthRange();
      double minDepth = theCamera.getClipDistance();
      double maxDepth = depthRange[1];
      if (maxDepth-minDepth < 0.01)
        maxDepth = minDepth+0.01;
      gl.glMatrixMode(GL.GL_PROJECTION);
      gl.glLoadIdentity();
      double scale = 0.01*theCamera.getClipDistance()/theCamera.getDistToScreen();
      gl.glFrustum(-0.5*width*scale, 0.5*width*scale, -0.5*height*scale, 0.5*height*scale, minDepth, maxDepth);

      for (ObjectInfo info : sortObjects())
      {
        renderObject(gl, info);
        if (System.currentTimeMillis()-updateTime > 5000)
        {
          createImage(gl);
          updateImage();
        }
      }

      createImage(gl);
    }

    public void displayChanged(GLAutoDrawable drawable, boolean arg1, boolean arg2)
    {
    }
  }
}