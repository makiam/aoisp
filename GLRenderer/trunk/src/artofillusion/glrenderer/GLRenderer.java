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

/** This is a Renderer which uses OpenGL to generate images. */

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
  private int vertBufferId, indexBufferId;
  private int displacementBufferIndex;
  private FloatBuffer displacementBuffer;
  private boolean cullingEnabled;
  private ShaderGenerator shaderGenerator;

  private static final int DISPLACEMENT_BUFFER_SIZE = 1024;

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
    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    shaderGenerator = new ShaderGenerator(theScene, theCamera);
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

    Threading.disableSingleThreading();
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
    shaderGenerator = null;
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

  /** Render an object to the image. */

  private void renderObject(GL gl, ObjectInfo info)
  {
    if (!info.visible)
      return;
    theCamera.setObjectTransform(info.coords.fromLocal());
    if (theCamera.visibility(info.getBounds()) == Camera.NOT_VISIBLE)
      return;
//    Thread currentThread = Thread.currentThread();
//    if (currentThread != renderThread)
//      return;
    Object3D theObject = info.object;
    while (theObject instanceof ObjectWrapper)
      theObject = ((ObjectWrapper) theObject).getWrappedObject();
    if (theObject instanceof ObjectCollection)
    {
      Enumeration objects = ((ObjectCollection) theObject).getObjects(info, false, theScene);
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
    Mat4 toView = theCamera.getObjectToView();
    gl.glMatrixMode(GL.GL_MODELVIEW);
    gl.glLoadMatrixd(new double [] {
        -toView.m11, toView.m21, -toView.m31, toView.m41,
        -toView.m12, toView.m22, -toView.m32, toView.m42,
        -toView.m13, toView.m23, -toView.m33, toView.m43,
        -toView.m14, toView.m24, -toView.m34, toView.m44
    }, 0);
    prepareCulling(gl, hideBackfaces && theObject.isClosed() && !theObject.getTexture().hasComponent(Texture.TRANSPARENT_COLOR_COMPONENT));

    // Set up the material properties to let OpenGL shade the surface.

    try
    {
      shaderGenerator.prepareShader(gl, info);
    }
    catch (IOException ex)
    {
      ex.printStackTrace();
    }

    // Handle displacement mapped objects.

    if (theObject.getTexture().hasComponent(Texture.DISPLACEMENT_COMPONENT))
    {
      renderMeshDisplaced(gl, mesh, tol);
      return;
    }

    // Map a buffer in which to store the vertices and normals.

    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertBufferId);
    gl.glBufferDataARB(GL.GL_ARRAY_BUFFER, mesh.triangle.length*18*BufferUtil.SIZEOF_FLOAT, null, GL.GL_STREAM_DRAW);
    ByteBuffer map = gl.glMapBuffer(GL.GL_ARRAY_BUFFER, GL.GL_WRITE_ONLY);
    map.order(ByteOrder.nativeOrder());
    FloatBuffer buffer = map.asFloatBuffer();
    gl.glVertexPointer(3, GL.GL_FLOAT, 6*BufferUtil.SIZEOF_FLOAT, 0);
    gl.glNormalPointer(GL.GL_FLOAT, 6*BufferUtil.SIZEOF_FLOAT, 3*BufferUtil.SIZEOF_FLOAT);

    // Set the vertices and normals from the mesh.

    for (RenderingTriangle tri : mesh.triangle)
    {
      Vec3 v = mesh.vert[tri.v1];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
      v = mesh.norm[tri.n1];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
      v = mesh.vert[tri.v2];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
      v = mesh.norm[tri.n2];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
      v = mesh.vert[tri.v3];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
      v = mesh.norm[tri.n3];
      buffer.put((float) v.x);
      buffer.put((float) v.y);
      buffer.put((float) v.z);
    }
    gl.glUnmapBuffer(GL.GL_ARRAY_BUFFER);
    gl.glDrawArrays(GL.GL_TRIANGLES, 0, mesh.triangle.length*3);
  }

  /** Vertices are accumulated in a buffer while rendering a displacement mapped object.
      This method draws the current contents of the buffer, then prepares it for further
      rendering. */

  private void flushDisplacementBuffer(GL gl, boolean currentlyMapped, boolean stillNeeded)
  {
    if (currentlyMapped && displacementBufferIndex > 0)
    {
      gl.glUnmapBuffer(GL.GL_ARRAY_BUFFER);
      gl.glDrawArrays(GL.GL_TRIANGLES, 0, displacementBufferIndex*3);
    }
    displacementBufferIndex = 0;
    if (stillNeeded)
    {
      gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertBufferId);
      gl.glBufferDataARB(GL.GL_ARRAY_BUFFER, DISPLACEMENT_BUFFER_SIZE*18*BufferUtil.SIZEOF_FLOAT, null, GL.GL_STREAM_DRAW);
      ByteBuffer map = gl.glMapBuffer(GL.GL_ARRAY_BUFFER, GL.GL_WRITE_ONLY);
      map.order(ByteOrder.nativeOrder());
      displacementBuffer = map.asFloatBuffer();
      gl.glVertexPointer(3, GL.GL_FLOAT, 6*BufferUtil.SIZEOF_FLOAT, 0);
      gl.glNormalPointer(GL.GL_FLOAT, 6*BufferUtil.SIZEOF_FLOAT, 3*BufferUtil.SIZEOF_FLOAT);
    }
  }

  /** Render a displacement mapped triangle mesh by recursively subdividing the triangles
      until they are sufficiently small. */

  private void renderMeshDisplaced(GL gl, RenderingMesh mesh, double tol)
  {
    Vec3 vert[] = mesh.vert, norm[] = mesh.norm;
    Mat4 toView = theCamera.getObjectToView();
    Vec3 temp1 = new Vec3(), temp2 = new Vec3();

    flushDisplacementBuffer(gl, false, true);
    for (RenderingTriangle tri : mesh.triangle)
    {
      int v1 = tri.v1;
      int v2 = tri.v2;
      int v3 = tri.v3;
      int n1 = tri.n1;
      int n2 = tri.n2;
      int n3 = tri.n3;
      double dist1 = vert[v1].distance(vert[v2]);
      double dist2 = vert[v2].distance(vert[v3]);
      double dist3 = vert[v3].distance(vert[v1]);

      // Calculate the gradient vectors for u and v.

      temp1.set(vert[v1].x-vert[v3].x, vert[v1].y-vert[v3].y, vert[v1].z-vert[v3].z);
      temp2.set(vert[v3].x-vert[v2].x, vert[v3].y-vert[v2].y, vert[v3].z-vert[v2].z);
      Vec3 vgrad = temp1.cross(mesh.faceNorm[tri.index]);
      Vec3 ugrad = temp2.cross(mesh.faceNorm[tri.index]);
      vgrad.scale(-1.0/vgrad.dot(temp2));
      ugrad.scale(1.0/ugrad.dot(temp1));
      DisplacedVertex dv1 = new DisplacedVertex(tri, vert[v1], norm[n1], 1.0, 0.0, toView, ugrad, vgrad);
      DisplacedVertex dv2 = new DisplacedVertex(tri, vert[v2], norm[n2], 0.0, 1.0, toView, ugrad, vgrad);
      DisplacedVertex dv3 = new DisplacedVertex(tri, vert[v3], norm[n3], 0.0, 0.0, toView, ugrad, vgrad);
      renderDisplacedTriangle(gl, tri, dv1, dist1, dv2, dist2, dv3, dist3, ugrad, vgrad, tol);
    }
    flushDisplacementBuffer(gl, true, false);
  }

  /** Render a displacement mapeed triangle by recursively subdividing it. */

  private void renderDisplacedTriangle(GL gl, RenderingTriangle tri, DisplacedVertex dv1,
                                       double dist1, DisplacedVertex dv2, double dist2, DisplacedVertex dv3, double dist3,
                                       Vec3 ugrad, Vec3 vgrad, double tol)
  {
    Mat4 toView = theCamera.getObjectToView();
    DisplacedVertex midv1 = null, midv2 = null, midv3 = null;
    double halfdist1 = 0, halfdist2 = 0, halfdist3 = 0;
    boolean split1 = dist1 > tol, split2 = dist2 > tol, split3 = dist3 > tol;
    int count = 0;

    if (split1)
    {
      midv1 = new DisplacedVertex(tri, new Vec3(0.5*(dv1.vert.x+dv2.vert.x), 0.5*(dv1.vert.y+dv2.vert.y), 0.5*(dv1.vert.z+dv2.vert.z)),
        new Vec3(0.5*(dv1.norm.x+dv2.norm.x), 0.5*(dv1.norm.y+dv2.norm.y), 0.5*(dv1.norm.z+dv2.norm.z)),
        0.5*(dv1.u+dv2.u), 0.5*(dv1.v+dv2.v), toView, ugrad, vgrad);
      halfdist1 = 0.5*dist1;
      count++;
    }
    if (split2)
    {
      midv2 = new DisplacedVertex(tri, new Vec3(0.5*(dv2.vert.x+dv3.vert.x), 0.5*(dv2.vert.y+dv3.vert.y), 0.5*(dv2.vert.z+dv3.vert.z)),
        new Vec3(0.5*(dv2.norm.x+dv3.norm.x), 0.5*(dv2.norm.y+dv3.norm.y), 0.5*(dv2.norm.z+dv3.norm.z)),
        0.5*(dv2.u+dv3.u), 0.5*(dv2.v+dv3.v), toView, ugrad, vgrad);
      halfdist2 = 0.5*dist2;
      count++;
    }
    if (split3)
    {
      midv3 = new DisplacedVertex(tri, new Vec3(0.5*(dv3.vert.x+dv1.vert.x), 0.5*(dv3.vert.y+dv1.vert.y), 0.5*(dv3.vert.z+dv1.vert.z)),
        new Vec3(0.5*(dv3.norm.x+dv1.norm.x), 0.5*(dv3.norm.y+dv1.norm.y), 0.5*(dv3.norm.z+dv1.norm.z)),
        0.5*(dv3.u+dv1.u), 0.5*(dv3.v+dv1.v), toView, ugrad, vgrad);
      halfdist3 = 0.5*dist3;
      count++;
    }

    // If any side is still too large, subdivide the triangle further.

    if (count == 1)
    {
      // Split it into two triangles.

      if (split1)
      {
        double d = dv3.vert.distance(midv1.vert);
        renderDisplacedTriangle(gl, tri, dv1, halfdist1, midv1, d, dv3, dist3,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, midv1, halfdist1, dv2, dist2, dv3, d,
            ugrad, vgrad, tol);
      }
      else if (split2)
      {
        double d = dv1.vert.distance(midv2.vert);
        renderDisplacedTriangle(gl, tri, dv2, halfdist2, midv2, d, dv1, dist1,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, midv2, halfdist2, dv3, dist3, dv1, d,
            ugrad, vgrad, tol);
      }
      else
      {
        double d = dv1.vert.distance(midv3.vert);
        renderDisplacedTriangle(gl, tri, dv3, halfdist3, midv3, d, dv2, dist2,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, midv3, halfdist3, dv1, dist1, dv2, d,
            ugrad, vgrad, tol);
      }
      return;
    }
    if (count == 2)
    {
      // Split it into three triangles.

      if (!split1)
      {
        double d1 = midv2.vert.distance(dv1.vert), d2 = midv2.vert.distance(midv3.vert);
        renderDisplacedTriangle(gl, tri, dv1, dist1, dv2, halfdist2, midv2, d1,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, dv1, d1, midv2, d2, midv3, halfdist3,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, dv3, halfdist3, midv3, d2, midv2, halfdist2,
            ugrad, vgrad, tol);
      }
      else if (!split2)
      {
        double d1 = midv3.vert.distance(dv2.vert), d2 = midv3.vert.distance(midv1.vert);
        renderDisplacedTriangle(gl, tri, dv2, dist2, dv3, halfdist3, midv3, d1,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, dv2, d1, midv3, d2, midv1, halfdist1,
            ugrad, vgrad, tol );
        renderDisplacedTriangle(gl, tri, dv1, halfdist1, midv1, d2, midv3, halfdist3,
            ugrad, vgrad, tol);
      }
      else
      {
        double d1 = midv1.vert.distance(dv3.vert), d2 = midv1.vert.distance(midv2.vert);
        renderDisplacedTriangle(gl, tri, dv3, dist3, dv1, halfdist1, midv1, d1,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, dv3, d1, midv1, d2, midv2, halfdist2,
            ugrad, vgrad, tol);
        renderDisplacedTriangle(gl, tri, dv2, halfdist2, midv2, d2, midv1, halfdist1,
            ugrad, vgrad, tol);
      }
      return;
    }
    if (count == 3)
    {
      // Split it into four triangles.

      double d1 = midv1.vert.distance(midv2.vert), d2 = midv2.vert.distance(midv3.vert), d3 = midv3.vert.distance(midv1.vert);
      renderDisplacedTriangle(gl, tri, dv1, halfdist1, midv1, d3, midv3, halfdist3,
          ugrad, vgrad, tol);
      renderDisplacedTriangle(gl, tri, dv2, halfdist2, midv2, d1, midv1, halfdist1,
          ugrad, vgrad, tol);
      renderDisplacedTriangle(gl, tri, dv3, halfdist3, midv3, d2, midv2, halfdist2,
          ugrad, vgrad, tol);
      renderDisplacedTriangle(gl, tri, midv1, d1, midv2, d2, midv3, d3,
          ugrad, vgrad, tol);
      return;
    }

    // The triangle is small enough that it does not need to be split any more, so render it.

    Vec3 v = dv1.dispvert;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    v = dv1.dispnorm;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    v = dv2.dispvert;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    v = dv2.dispnorm;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    v = dv3.dispvert;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    v = dv3.dispnorm;
    displacementBuffer.put((float) v.x);
    displacementBuffer.put((float) v.y);
    displacementBuffer.put((float) v.z);
    displacementBufferIndex++;
    if (displacementBufferIndex == DISPLACEMENT_BUFFER_SIZE)
      flushDisplacementBuffer(gl, true, true);
  }

  /** This is an inner class for keeping track of information about vertices when
     doing displacement mapping. */

  private class DisplacedVertex
  {
    public Vec3 vert, norm, dispvert, dispnorm;
    public double u, v;

    public DisplacedVertex(RenderingTriangle tri, Vec3 vert, Vec3 norm, double u, double v,
                           Mat4 toView, Vec3 ugrad, Vec3 vgrad)
    {
      this.vert = vert;
      this.norm = norm;
      this.u = u;
      this.v = v;
      double z = (float) toView.timesZ(vert);
      double tol = (z > theCamera.getDistToScreen()) ? smoothScale*z : smoothScale;
      double disp = tri.getDisplacement(u, v, 1.0-u-v, tol, 0.0);
      dispvert = new Vec3(vert.x+disp*norm.x, vert.y+disp*norm.y, vert.z+disp*norm.z);

      // Find the derivatives of the displacement map, and use them to find the
      // local normal vector.

      double w = 1.0-u-v;
      double dhdu = (tri.getDisplacement(u+(1e-5), v, w-(1e-5), tol, 0.0)-disp)*1e5;
      double dhdv = (tri.getDisplacement(u, v+(1e-5), w-(1e-5), tol, 0.0)-disp)*1e5;
      dispnorm = new Vec3(norm);
      Vec3 temp = new Vec3(dhdu*ugrad.x+dhdv*vgrad.x, dhdu*ugrad.y+dhdv*vgrad.y, dhdu*ugrad.z+dhdv*vgrad.z);
      dispnorm.scale(temp.dot(dispnorm)+1.0);
      dispnorm.subtract(temp);
      dispnorm.normalize();
    }
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

      // Prepare for rendering with Vertex Buffer Objects.

      int bufferIds[] = new int [2];
      gl.glGenBuffers(2, bufferIds, 0);
      vertBufferId = bufferIds[0];
      indexBufferId = bufferIds[1];
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