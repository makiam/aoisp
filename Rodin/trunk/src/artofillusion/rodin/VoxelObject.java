/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.object.*;
import artofillusion.math.*;
import artofillusion.*;
import artofillusion.ui.*;
import artofillusion.animation.*;

import java.util.*;
import java.io.*;

public class VoxelObject extends ImplicitObject
{
  private VoxelOctree voxels;
  private double scale;
  private WireframeMesh cachedWire;
  private RenderingMesh cachedMesh;
  private BoundingBox cachedBounds;
  private boolean voxelsAreShared;

  public VoxelObject(int depth)
  {
    voxels = new VoxelOctree(depth);
    scale = 1.0;
  }

  public double getScale()
  {
    return scale;
  }

  public void setScale(double scale)
  {
    this.scale = scale;
  }

  public double getFieldValue(double x, double y, double z, double size, double time)
  {
    double invScale = 1.0/scale;
    int indexScale = (1<<voxels.getDepth())-1;
    double x0 = (x*invScale+0.5)*indexScale;
    double y0 = (y*invScale+0.5)*indexScale;
    double z0 = (z*invScale+0.5)*indexScale;
    int i0 = (int) x0;
    int j0 = (int) y0;
    int k0 = (int) z0;
    int i1 = (i0 == indexScale ? i0 : i0+1);
    int j1 = (j0 == indexScale ? j0 : j0+1);
    int k1 = (k0 == indexScale ? k0 : k0+1);
    double wx1 = x0-i0;
    double wy1 = y0-j0;
    double wz1 = z0-k0;
    double wx0 = 1.0-wx1;
    double wy0 = 1.0-wy1;
    double wz0 = 1.0-wz1;
    return voxels.getValue(i0, j0, k0)*wx0*wy0*wz0
          +voxels.getValue(i0, j0, k1)*wx0*wy0*wz1
          +voxels.getValue(i0, j1, k0)*wx0*wy1*wz0
          +voxels.getValue(i0, j1, k1)*wx0*wy1*wz1
          +voxels.getValue(i1, j0, k0)*wx1*wy0*wz0
          +voxels.getValue(i1, j0, k1)*wx1*wy0*wz1
          +voxels.getValue(i1, j1, k0)*wx1*wy1*wz0
          +voxels.getValue(i1, j1, k1)*wx1*wy1*wz1;
  }

  public boolean getPreferDirectRendering()
  {
    return true;
  }

  @Override
  public double getCutoff()
  {
    return 0.0;
  }

  public Object3D duplicate()
  {
    VoxelObject copy = new VoxelObject(1);
    copy.copyObject(this);
    return copy;
  }

  public void copyObject(Object3D obj)
  {
    VoxelObject vo = (VoxelObject) obj;
    voxels = vo.voxels;
    scale = vo.scale;
    voxelsAreShared = true;
    vo.voxelsAreShared = true;
    copyTextureAndMaterial(obj);
    clearCachedMeshes();
  }

  public BoundingBox getBounds()
  {
    if (cachedBounds == null)
    {
      int[] bounds = voxels.findDataBounds();
      double boundsScale = scale/voxels.getWidth();
      double boundsOffset = scale*0.5;
      cachedBounds = new BoundingBox(bounds[0]*boundsScale-boundsOffset,
          bounds[1]*boundsScale-boundsOffset,
          bounds[2]*boundsScale-boundsOffset,
          bounds[3]*boundsScale-boundsOffset,
          bounds[4]*boundsScale-boundsOffset,
          bounds[5]*boundsScale-boundsOffset);
    }
    return cachedBounds;
  }

  public void setSize(double xsize, double ysize, double zsize)
  {
    Vec3 size = getBounds().getSize();
    scale *= Math.max(Math.max(xsize/size.x, ysize/size.y), zsize/size.z);
    clearCachedMeshes();
  }

  public WireframeMesh getWireframeMesh()
  {
    if (cachedWire != null)
      return cachedWire;
    Vec3 vert[] = getBounds().getCorners();
    int from[] = new int [] {0, 2, 3, 1, 4, 6, 7, 5, 0, 1, 2, 3};
    int to[] = new int [] {2, 3, 1, 0, 6, 7, 5, 4, 4, 5, 6, 7};
    return (cachedWire = new WireframeMesh(vert, from, to));
  }

  @Override
  public RenderingMesh getRenderingMesh(double tol, boolean interactive, ObjectInfo info)
  {
    if (interactive && cachedMesh != null)
      return cachedMesh;
    ArrayList<Vec3> vertices = new ArrayList<Vec3>();
    ArrayList<int[]> faces = new ArrayList<int[]>();
    MarchingCubes.generateMesh(voxels, scale, vertices, faces);
    RenderingTriangle tri[] = new RenderingTriangle[faces.size()];
    Vec3 vert[] = vertices.toArray(new Vec3[vertices.size()]);
    for (int i = 0; i < tri.length; i++)
    {
      int face[] = faces.get(i);
      tri[i] = texMapping.mapTriangle(face[0], face[1], face[2], 0, 0, 0, vert);
    }
    Vec3 norm[] = new Vec3[] {null};
    RenderingMesh mesh = new RenderingMesh(vert, norm, tri, texMapping, matMapping);
    if (interactive)
      cachedMesh = mesh;
    return mesh;
  }

  public void optimizeValues()
  {
    int bounds[] = voxels.findDataBounds();
    if (bounds[5]-bounds[4] < 2)
        return;

    // Record values for faster access.

    int xsize = bounds[1]-bounds[0]+1;
    int ysize = bounds[3]-bounds[2]+1;
    byte values[][][] = new byte[3][xsize+2][ysize+2];
    for (int x = 0; x < xsize; x++)
      for (int y = 0; y < ysize; y++)
        for (int z = 0; z < 3; z++)
          values[z][x][y] = Byte.MIN_VALUE;
    for (int x = 1; x <= xsize; x++)
      for (int y = 1; y <= ysize; y++)
      {
        values[1][x][y] = voxels.getValue(bounds[0]+x-1, bounds[2]+y-1, bounds[4]);
        values[2][x][y] = voxels.getValue(bounds[0]+x-1, bounds[2]+y-1, bounds[4]+1);
      }

    // Loop over values and find ones that can be set to Byte.MIN_VALUE or Byte.MAX_VALUE.

    for (int z = bounds[4]; z <= bounds[5]; z++)
    {
      for (int x = 1; x <= xsize; x++)
        for (int y = 1; y <= ysize; y++)
        {
          // Count the number of positive and negative values surrounding this point.

          int numNegative = 0;
          int numPositive = 0;
          for (int i = -1; i < 2; i++)
            for (int j = -1; j < 2; j++)
              for (int k = 0; k < 3; k++)
              {
                byte val = values[k][x+i][y+j];
                if (val < 0)
                  numNegative++;
                else if (val > 0)
                  numPositive++;
              }
          if (numNegative == 27)
            voxels.setValue(x+bounds[0]-1, y+bounds[2]-1, z, Byte.MIN_VALUE);
          else if (numPositive == 27)
            voxels.setValue(x+bounds[0]-1, y+bounds[2]-1, z, Byte.MAX_VALUE);
        }

      // Load the next layer of values.

      byte temp[][] = values[0];
      values[0] = values[1];
      values[1] = values[2];
      values[2] = temp;
      for (int x = 1; x <= xsize; x++)
        for (int y = 1; y <= ysize; y++)
          values[2][x][y] = voxels.getValue(bounds[0]+x-1, bounds[2]+y-1, z+2);
    }
  }


  public Keyframe getPoseKeyframe()
  {
    return null;
  }

  public void applyPoseKeyframe(Keyframe k)
  {
  }

  @Override
  public boolean canSetTexture()
  {
    return true;
  }

  @Override
  public boolean canSetMaterial()
  {
    return true;
  }

  @Override
  public boolean isEditable()
  {
    return true;
  }

  @Override
  public void edit(EditingWindow parent, ObjectInfo info, Runnable cb)
  {
    VoxelObjectEditorWindow ed = new VoxelObjectEditorWindow(parent, info.getName(), info, cb);
    ed.setVisible(true);
  }

  @Override
  public int canConvertToTriangleMesh()
  {
    return APPROXIMATELY;
  }

  @Override
  public TriangleMesh convertToTriangleMesh(double tol)
  {
    ArrayList<Vec3> vertices = new ArrayList<Vec3>();
    ArrayList<int[]> faces = new ArrayList<int[]>();
    MarchingCubes.generateMesh(voxels, scale, vertices, faces);
    Vec3 vert[] = vertices.toArray(new Vec3[vertices.size()]);
    int face[][] = faces.toArray(new int[faces.size()][]);
    return new TriangleMesh(vert, face);
  }

  public VoxelOctree getVoxels()
  {
    if (voxelsAreShared)
    {
      voxels = voxels.duplicate();
      voxelsAreShared = false;
    }
    return voxels;
  }

  public void clearCachedMeshes()
  {
    cachedWire = null;
    cachedMesh = null;
    cachedBounds = null;
  }

  /**
   * Create a VoxelObject from the data stored in a file.
   */

  public VoxelObject(DataInputStream in, Scene theScene) throws IOException
  {
    super(in, theScene);
    scale = in.readDouble();
    voxels = new VoxelOctree(in.readInt());
    int minx = in.readInt();
    int maxx = in.readInt();
    int miny = in.readInt();
    int maxy = in.readInt();
    for (int i = minx; i <= maxx; i++)
      for (int j = miny; j <= maxy; j++)
      {
        int first = in.readShort();
        int count = in.readShort();
        for (int k = 0; k < count; k++)
          voxels.setValue(i, j, first+k, in.readByte());
      }
  }

  /**
   * Save the object to a file.
   */

  public void writeToFile(DataOutputStream out, Scene theScene) throws IOException
  {
    super.writeToFile(out, theScene);
    out.writeDouble(scale);
    int depth = voxels.getDepth();
    out.writeInt(depth);
    int bounds[] = voxels.findDataBounds();
    out.writeInt(bounds[0]);
    out.writeInt(bounds[1]);
    out.writeInt(bounds[2]);
    out.writeInt(bounds[3]);
    for (int i = bounds[0]; i <= bounds[1]; i++)
      for (int j = bounds[2]; j <= bounds[3]; j++)
      {
        int first, last;
        for (first = bounds[4]; first <= bounds[5] && voxels.getValue(i, j, first) == Byte.MIN_VALUE; first++);
        if (first > bounds[5])
          last = first-1;
        else
          for (last = bounds[5]; voxels.getValue(i, j, last) == Byte.MIN_VALUE; last--);
        out.writeShort(first-1);
        out.writeShort(last-first+1);
        for (int k = first; k <= last; k++)
          out.writeByte(voxels.getValue(i, j, k));
      }
  }
}
