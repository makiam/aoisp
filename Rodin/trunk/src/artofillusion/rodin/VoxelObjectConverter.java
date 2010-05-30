/* Copyright (C) 2010 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.*;
import artofillusion.math.*;
import artofillusion.object.*;

import java.util.*;

public class VoxelObjectConverter
{
  private static double TOL = 1e-10;

  public static VoxelObject convertObject(ObjectInfo info, double accuracy)
  {
    Object3D obj = info.getObject();
    if (!obj.isClosed())
      throw new IllegalArgumentException("Only closed surfaces can be converted to voxel objects");
    BoundingBox bounds = obj.getBounds();
    double xwidth = bounds.maxx-bounds.minx;
    double ywidth = bounds.maxy-bounds.miny;
    double zwidth = bounds.maxz-bounds.minz;
    int xsize = (int) (xwidth/accuracy)+1;
    int ysize = (int) (ywidth/accuracy)+1;
    int zsize = (int) (zwidth/accuracy)+1;
    BitSet inside = new BitSet(xsize*ysize*zsize);
    RenderingMesh mesh = obj.getRenderingMesh(0.5*accuracy, false, info);

    // Record the bounds for each face of the mesh.

    double faceBounds[][] = new double[mesh.triangle.length][];
    for (int i = 0; i < faceBounds.length; i++)
    {
      RenderingTriangle tri = mesh.triangle[i];
      Vec3 v1 = mesh.vert[tri.v1];
      Vec3 v2 = mesh.vert[tri.v2];
      Vec3 v3 = mesh.vert[tri.v3];
      double minx = Math.min(Math.min(v1.x, v2.x), v3.x);
      double maxx = Math.max(Math.max(v1.x, v2.x), v3.x);
      double miny = Math.min(Math.min(v1.y, v2.y), v3.y);
      double maxy = Math.max(Math.max(v1.y, v2.y), v3.y);
      faceBounds[i] = new double[] {minx-TOL, maxx+TOL, miny-TOL, maxy+TOL};
    }

    // Process each row of the grid.

    for (int i = 0; i < xsize; i++)
    {
      double x = bounds.minx+i*xwidth/(xsize-1);
      for (int j = 0; j < ysize; j++)
      {
        double y = bounds.miny+j*ywidth/(ysize-1);

        // Record where each face intersects this row.

        ArrayList<SortRecord> sortedFaces = new ArrayList<SortRecord>();
        for (int face = 0; face < faceBounds.length; face++)
        {
          if (faceBounds[face][0] > x || faceBounds[face][1] < x || faceBounds[face][2] > y || faceBounds[face][3] < y)
            continue;
          RenderingTriangle tri = mesh.triangle[face];
          Vec3 v1 = mesh.vert[tri.v1];
          Vec3 v2 = mesh.vert[tri.v2];
          Vec3 v3 = mesh.vert[tri.v3];
          double e1x = v1.x-v2.x;
          double e1y = v1.y-v2.y;
          double e2x = v1.x-v3.x;
          double e2y = v1.y-v3.y;
          double denom = 1.0/(e1x*e2y-e1y*e2x);
          e1x *= denom;
          e1y *= denom;
          e2x *= denom;
          e2y *= denom;
          double vx = x - v1.x;
          double vy = y - v1.y;
          double v = e2x*vy - e2y*vx;
          if (v < -TOL || v > 1.0+TOL)
            continue;
          double w = vx*e1y - vy*e1x;
          if (w < -TOL || w > 1.0+TOL)
            continue;
          double u = 1.0-v-w;
          if (u < -TOL || u > 1.0+TOL)
            continue;
          double z = u*v1.z + v*v2.z + w*v3.z;
          sortedFaces.add(new SortRecord(z, mesh.faceNorm[face]));
        }

        // Sort them, then walk through the list marking which voxels are inside.

        Collections.sort(sortedFaces);
        boolean currentlyInside = false;
        int k = 0;
        for (SortRecord face : sortedFaces)
        {
          int nextk = (int) Math.round(zsize*(face.z-bounds.minz)/zwidth);
          if (currentlyInside)
            for (; k < nextk; k++)
              inside.set(i+j*xsize+k*xsize*ysize);
          k = nextk;
          currentlyInside = face.entering;
        }
      }
    }

    // Create the VoxelObject.

    int depth = 1;
    while ((1 << depth) < xsize || (1 << depth) < ysize || (1 << depth) < zsize)
      depth++;
    int startx = ((1<<depth)-xsize)/2;
    int starty = ((1<<depth)-ysize)/2;
    int startz = ((1<<depth)-zsize)/2;
    VoxelObject voxel = new VoxelObject(depth);

    // Determine the weight to use when smoothing the surface.
    int radius = 4;
    int radius2 = radius*radius;
    int maskCount = 0;
    for (int i = -radius; i <= radius; i++)
      for (int j = -radius; j <= radius; j++)
        for (int k = -radius; k <= radius; k++)
          if (i*i+j*j+k*k <= radius2)
            maskCount++;
    double valueScale = 255.0/maskCount;

    // Compute the voxels.

    for (int i = -radius+1; i < xsize+radius-1; i++)
      for (int j = -radius+1; j < ysize+radius-1; j++)
        for (int k = -radius+1; k < zsize+radius-1; k++)
        {
          // Determine whether this voxel is fully inside, fully outside, or near the surface.

          int count = 0;
          for (int xoffset = -1; xoffset <= 1; xoffset++)
          {
            int x = i+xoffset;
            if (x < 0 || x >= xsize)
              continue;
            for (int yoffset = -1; yoffset <= 1; yoffset++)
            {
              int y = j+yoffset;
              if (y < 0 || y >= ysize)
                continue;
              for (int zoffset = -1; zoffset <= 1; zoffset++)
              {
                int z = k+zoffset;
                if (z < 0 || z >= zsize)
                  continue;
                if (inside.get(x+y*xsize+z*xsize*ysize))
                  count++;
              }
            }
          }
          if (count == 0)
            continue;
          if (count == 27)
            voxel.getVoxels().setValue(i+startx, j+starty, k+startz, Byte.MAX_VALUE);
          else
          {
            // This voxel is near the surface, so compute its value by averaging the local neighborhood.
            
            count = 0;
            for (int xoffset = -radius; xoffset <= radius; xoffset++)
            {
              int x = i+xoffset;
              if (x < 0 || x >= xsize)
                continue;
              for (int yoffset = -radius; yoffset <= radius; yoffset++)
              {
                int offset2 = xoffset*xoffset + yoffset*yoffset;
                if (offset2 > radius2)
                  continue;
                int y = j+yoffset;
                if (y < 0 || y >= ysize)
                  continue;
                for (int zoffset = -radius; zoffset <= radius; zoffset++)
                {
                  if (offset2 + zoffset*zoffset > radius2)
                    continue;
                  int z = k+zoffset;
                  if (z < 0 || z >= zsize)
                    continue;
                  if (inside.get(x+y*xsize+z*xsize*ysize))
                    count++;
                }
              }
            }
            voxel.getVoxels().setValue(i+startx, j+starty, k+startz, (byte) (Byte.MIN_VALUE+Math.round(valueScale*count)));
          }
        }
    voxel.copyTextureAndMaterial(obj);
    voxel.setSize(xwidth, ywidth, zwidth);
    return voxel;
  }

  private static class SortRecord implements Comparable<SortRecord>
  {
    boolean entering;
    double z;

    public SortRecord(double z, Vec3 faceNorm)
    {
      this.z = z;
      entering = (faceNorm.z <= 0);
    }

    public int compareTo(SortRecord o)
    {
      if (z < o.z)
        return -1;
      if (z == o.z)
        return 0;
      return 1;
    }
  }
}
