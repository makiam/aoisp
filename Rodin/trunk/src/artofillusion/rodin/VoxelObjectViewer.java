/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.*;
import artofillusion.ui.*;
import artofillusion.object.*;
import artofillusion.util.*;
import artofillusion.view.*;
import artofillusion.math.*;
import buoy.widget.*;
import buoy.event.*;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class VoxelObjectViewer extends ViewerCanvas
{
  private VoxelObjectEditorWindow window;
  private CoordinateSystem lastCoords;
  private boolean lastPerspective;
  private double lastScale;
  private ArrayList<BoundingBox> changedRegions;
  private int pixel[];

  public VoxelObjectViewer(VoxelObjectEditorWindow window, RowContainer controls)
  {
    super(false);
    this.window = window;
    changedRegions = new ArrayList<BoundingBox>();
    buildChoices(controls);
  }

  public VoxelObjectEditorWindow getWindow()
  {
    return window;
  }

  public double[] estimateDepthRange()
  {
    return new double[0];
  }

  /** This should be called whenever the VoxelObject has changed. */

  public void voxelsChanged()
  {
    lastCoords = null;
  }

  /** This should be called whenever a block of values in the VoxelObject have changed. */

  public void voxelsChanged(int fromx, int tox, int fromy, int toy, int fromz, int toz)
  {
    VoxelObject obj = (VoxelObject) window.getObject().getObject();
    int width = obj.getVoxels().getWidth();
    double scale = obj.getScale()/(width-1);
    changedRegions.add(new BoundingBox((fromx-1-0.5*(width-1))*scale, (tox+1-0.5*(width-1))*scale,
        (fromy-1-0.5*(width-1))*scale, (toy+1-0.5*(width-1))*scale, (fromz-1-0.5*(width-1))*scale,
        (toz+1-0.5*(width-1))*scale));
  }

  @Override
  public void updateImage()
  {
    super.updateImage();

    // If the view has changed size, we need to recreate the buffer.

    Rectangle bounds = getBounds();
    if (pixel == null || pixel.length != bounds.width*bounds.height)
    {
      pixel = new int[bounds.width*bounds.height];
      lastCoords = null;
    }

    // Identify the range of pixels that need to be updated.

    int minx, maxx, miny, maxy;
    if (lastCoords == null || !lastCoords.getOrigin().equals(theCamera.getCameraCoordinates().getOrigin())
        || !lastCoords.getUpDirection().equals(theCamera.getCameraCoordinates().getUpDirection())
        || !lastCoords.getZDirection().equals(theCamera.getCameraCoordinates().getZDirection())
        || lastPerspective != theCamera.isPerspective()
        || lastScale != theCamera.getScale())
    {
      // Update the whole image.

      lastCoords = theCamera.getCameraCoordinates().duplicate();
      lastPerspective = theCamera.isPerspective();
      lastScale = theCamera.getScale();
      minx = miny = 0;
      maxx = bounds.width;
      maxy = bounds.height;
    }
    else
    {
      // Find a box containing all changed regions.

      minx = miny = Integer.MAX_VALUE;
      maxx = maxy = Integer.MIN_VALUE;
      theCamera.setObjectTransform(window.getObject().getCoords().fromLocal());
      Mat4 toScreen = theCamera.getObjectToScreen();
      for (BoundingBox region : changedRegions)
      {
        BoundingBox screenRegion = region.transformAndOutset(toScreen);
        if (screenRegion.minx < minx)
          minx = (int) screenRegion.minx;
        if (screenRegion.maxx > maxx)
          maxx = (int) screenRegion.maxx;
        if (screenRegion.miny < miny)
          miny = (int) screenRegion.miny;
        if (screenRegion.maxy > maxy)
          maxy = (int) screenRegion.maxy;
      }
      if (minx < 0)
        minx = 0;
      if (maxx > bounds.width)
        maxx = bounds.width;
      if (miny < 0)
        miny = 0;
      if (maxy > bounds.height)
        maxy = bounds.height;
    }
    changedRegions.clear();

    // Update the image of the VoxelObject.

    if (maxx > minx)
    {
      ThreadManager threads = getWindow().getThreadManager();
      threads.setNumIndices((maxx-minx)*(maxy-miny));
      threads.setTask(new RenderTask(minx, maxx, miny));
      threads.run();
    }

    // Draw the VoxelObject into the canvas.

    SoftwareCanvasDrawer drawer = (SoftwareCanvasDrawer) getCanvasDrawer();
    int viewPixel[] = ((DataBufferInt) ((BufferedImage) drawer.getImage()).getRaster().getDataBuffer()).getData();
    for (int i = 0; i < bounds.width; i++)
        for (int j = 0; j < bounds.height; j++)
          if (pixel[i+j*bounds.width] != 0)
            viewPixel[i+j*bounds.width] = pixel[i+j*bounds.width];

    // Finish up.

    drawBorder();
    if (showAxes)
      drawCoordinateAxes();
  }
  protected void mousePressed(WidgetMouseEvent e)
  {
    requestFocus();

    // Determine which tool is active.

    if (metaTool != null && e.isMetaDown())
      activeTool = metaTool;
    else if (altTool != null && e.isAltDown())
      activeTool = altTool;
    else
      activeTool = currentTool;
    activeTool.mousePressed(e, this);
  }

  protected void mouseDragged(WidgetMouseEvent e)
  {
    activeTool.mouseDragged(e, this);
  }

  protected void mouseReleased(WidgetMouseEvent e)
  {
    activeTool.mouseReleased(e, this);
    currentTool.getWindow().updateMenus();
  }

  public void previewObject()
  {
    Scene sc = new Scene();
    Renderer rend = ArtOfIllusion.getPreferences().getObjectPreviewRenderer();

    if (rend == null)
      return;
    sc.addObject(new DirectionalLight(new RGBColor(1.0f, 1.0f, 1.0f), 0.8f), theCamera.getCameraCoordinates(), "", null);
    ObjectInfo obj = window.getObject();
    sc.addObject(obj.duplicate(obj.getObject().duplicate()), null);
    adjustCamera(true);
    rend.configurePreview();
    ObjectInfo cameraInfo = new ObjectInfo(new SceneCamera(), theCamera.getCameraCoordinates(), "");
    new RenderingDialog(UIUtilities.findFrame(this), rend, sc, theCamera, cameraInfo);
    adjustCamera(isPerspective());
  }

  /**
   * This is a ThreadManager task for rendering the viewing in parallel.
   */

  private class RenderTask implements ThreadManager.Task
  {
    private int viewWidth, regionWidth, xoffset, yoffset;
    private Vec3 origin, direction, base, dx, dy, viewDir;
    private VoxelTracer tracer;
    ThreadLocal<ThreadInfo> threadInfo = new ThreadLocal<ThreadInfo>() {
      @Override
      protected ThreadInfo initialValue()
      {
        ThreadInfo info = new ThreadInfo();
        info.origin = new Vec3(origin);
        info.direction = new Vec3(direction);
        info.normal = new Vec3();
        info.color = new RGBColor();
        return info;
      }
    };

    public RenderTask(int minx, int maxx, int miny)
    {
      Mat4 toLocal = getWindow().getObject().getCoords().toLocal();
      if (theCamera.isPerspective())
      {
        origin = theCamera.getCameraCoordinates().getOrigin();
        toLocal.transform(origin);
        direction = new Vec3();
        base = theCamera.convertScreenToWorld(new Point(0, 0), 1.0, false);
        dx = theCamera.convertScreenToWorld(new Point(1, 0), 1.0, false).minus(base);
        dy = theCamera.convertScreenToWorld(new Point(0, 1), 1.0, false).minus(base);
      }
      else
      {
        origin = new Vec3();
        direction = theCamera.getCameraCoordinates().getZDirection();
        toLocal.transformDirection(direction);
        base = theCamera.convertScreenToWorld(new Point(0, 0), 0.0, false);
        dx = theCamera.convertScreenToWorld(new Point(1, 0), 0.0, false).minus(base);
        dy = theCamera.convertScreenToWorld(new Point(0, 1), 0.0, false).minus(base);
      }
      xoffset = minx;
      yoffset = miny;
      regionWidth = maxx-minx;
      viewWidth = getBounds().width;
      viewDir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
      tracer = getWindow().getVoxelTracer();
    }

    public void execute(int index)
    {
      int i = index%regionWidth+xoffset;
      int j = index/regionWidth+yoffset;
      ThreadInfo info = threadInfo.get();
      if (theCamera.isPerspective())
      {
        info.direction.set(base.x+i*dx.x+j*dy.x, base.y+i*dx.y+j*dy.y, base.z+i*dx.z+j*dy.z);
        info.direction.subtract(origin);
        info.direction.normalize();
      }
      else
      {
        info.origin.set(base.x+i*dx.x+j*dy.x, base.y+i*dx.y+j*dy.y, base.z+i*dx.z+j*dy.z);
      }
      double dist = tracer.findRayIntersection(info.origin, info.direction, info.normal);
      if (dist > 0)
      {
        info.color.copy(surfaceRGBColor);
        info.color.scale(0.1f+0.8f*Math.abs((float) viewDir.dot(info.normal)));
        pixel[i+j*viewWidth] = info.color.getARGB();
      }
      else
        pixel[i+j*viewWidth] = 0;
    }

    public void cleanup()
    {
    }

    private class ThreadInfo
    {
      public Vec3 origin, direction, normal;
      public RGBColor color;
    }
  }
}
