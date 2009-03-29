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

public class VoxelObject extends ImplicitObject
{
  private VoxelOctree voxels;
  private double scale;
  private WireframeMesh cachedWire;
  private RenderingMesh cachedMesh;
  private boolean voxelsAreShared;

  public VoxelObject(int depth)
  {
    voxels = new VoxelOctree(depth, 0.0f);
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
    return 0.5;
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
    double halfSize = 0.5*scale;
    return new BoundingBox(-halfSize, halfSize, -halfSize, halfSize, -halfSize, halfSize);
  }

  public void setSize(double xsize, double ysize, double zsize)
  {
    scale = xsize;
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
    MarchingCubes.generateMesh(voxels, scale, 0.5f, vertices, faces);
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
    MarchingCubes.generateMesh(voxels, scale, 0.5f, vertices, faces);
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
  }
}
