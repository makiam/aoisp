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
import buoy.widget.*;

import java.util.*;

public class VoxelObjectConverter
{
  private static final double TOL = 1e-10;

  /**
   * Convert an arbitrary object into a VoxelObject.
   */
  public static VoxelObject convertObject(ObjectInfo info, double accuracy, BProgressBar progress)
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
    RenderingMesh mesh = obj.getRenderingMesh(0.25*accuracy, false, info);
    final Thread thread = Thread.currentThread();

    // Record the bounds for each face of the mesh.

    BoundingBox faceBounds[] = new BoundingBox[mesh.triangle.length];
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
      double minz = Math.min(Math.min(v1.z, v2.z), v3.z);
      double maxz = Math.max(Math.max(v1.z, v2.z), v3.z);
      faceBounds[i] = new BoundingBox(minx-TOL, maxx+TOL, miny-TOL, maxy+TOL, minz-TOL, maxz+TOL);
    }

    // Process each row of the grid.

    if (progress != null)
    {
      progress.setProgressText("Identifying interior...");
      progress.setMinimum(0);
      progress.setMaximum(xsize);
    }
    for (int i = 0; i < xsize; i++)
    {
      if (progress != null)
        progress.setValue(i);
      if (thread.isInterrupted())
        return null;
      double x = bounds.minx+i*xwidth/(xsize-1);
      for (int j = 0; j < ysize; j++)
      {
        double y = bounds.miny+j*ywidth/(ysize-1);

        // Record where each face intersects this row.

        ArrayList<SortRecord> sortedFaces = new ArrayList<SortRecord>();
        for (int face = 0; face < faceBounds.length; face++)
        {
          if (faceBounds[face].minx > x || faceBounds[face].maxx < x || faceBounds[face].miny > y || faceBounds[face].maxy < y)
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
          int nextk = (int) Math.ceil((zsize-1)*(face.z-bounds.minz)/zwidth);
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

    // Compute the voxels.

    double faceDistance[] = new double[mesh.triangle.length];
    for (int i = 0; i < faceDistance.length; i++)
        faceDistance[i] = mesh.faceNorm[i].dot(mesh.vert[mesh.triangle[i].v1]);
    if (progress != null)
      progress.setProgressText("Creating surface...");
    for (int i = 0; i < xsize; i++)
    {
      if (progress != null)
        progress.setValue(i);
      if (thread.isInterrupted())
        return null;
      for (int j = 0; j < ysize; j++)
      {
        for (int k = 0; k < zsize; k++)
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
            double x = bounds.minx+i*xwidth/(xsize-1);
            double y = bounds.miny+j*ywidth/(ysize-1);
            double z = bounds.minz+k*zwidth/(zsize-1);
            double minDistance = 2*accuracy;
            for (int face = 0; face < faceBounds.length; face++)
            {
              if (faceBounds[face].minx-minDistance > x || faceBounds[face].maxx+minDistance < x || faceBounds[face].miny-minDistance > y || faceBounds[face].maxy+minDistance < y || faceBounds[face].minz-minDistance > z || faceBounds[face].maxz+minDistance < z)
                continue;
              Vec3 faceNorm = mesh.faceNorm[face];
              double distToPlane = faceNorm.x*x + faceNorm.y*y + faceNorm.z*z - faceDistance[face];
              if (distToPlane > minDistance || distToPlane < -minDistance)
                continue;
              double dist2 = distance2ToFace(x, y, z, mesh, face);
              if (dist2 < minDistance*minDistance)
                minDistance = Math.sqrt(dist2);
            }
            boolean isInside = (i >= 0 && i < xsize && j >= 0 && j < ysize && k >= 0 && k < zsize && inside.get(i+j*xsize+k*xsize*ysize));
            double value;
            if (isInside)
              value = 127*minDistance/(2*accuracy);
            else
              value = -128*minDistance/(2*accuracy);
            voxel.getVoxels().setValue(i+startx, j+starty, k+startz, (byte) Math.round(value));
          }
        }
      }
    }
    voxel.copyTextureAndMaterial(obj);
    voxel.setSize(xwidth, ywidth, zwidth);
    return voxel;
  }

  /**
   * Compute the squared distance between a point and a face.  This is based on the method by David Eberly
   * found at http://www.geometrictools.com/Documentation/DistancePoint3Triangle3.pdf.
   */
  private static double distance2ToFace(double x, double y, double z, RenderingMesh mesh, int face)
  {
    RenderingTriangle tri = mesh.triangle[face];
    Vec3 vert1 = mesh.vert[tri.v1];
    Vec3 vert2 = mesh.vert[tri.v2];
    Vec3 vert3 = mesh.vert[tri.v3];
    double e0x = vert2.x-vert1.x;
    double e0y = vert2.y-vert1.y;
    double e0z = vert2.z-vert1.z;
    double e1x = vert3.x-vert1.x;
    double e1y = vert3.y-vert1.y;
    double e1z = vert3.z-vert1.z;
    double deltax = vert1.x-x;
    double deltay = vert1.y-y;
    double deltaz = vert1.z-z;
    double a = e0x*e0x + e0y*e0y + e0z*e0z;
    double b = e0x*e1x + e0y*e1y + e0z*e1z;
    double c = e1x*e1x + e1y*e1y + e1z*e1z;
    double d = e0x*deltax + e0y*deltay + e0z*deltaz;
    double e = e1x*deltax + e1y*deltay + e1z*deltaz;
    double det = a*c-b*b;
    double s = b*e-c*d;
    double t = b*d-a*e;
    if (s+t <= det) {
        if (s < 0) {
            if (t < 0) {
                // Region 4

                if (d < 0) {
                    s = (-d >= a ? 1 : -d/a);
                    t = 0;
                }
                else {
                    s = 0;
                    t = (e >= 0 ? 0 : (-e >= c ? 1 : -e/c));
                }
            }
            else {
                // Region 3

                s = 0;
                t = (e >= 0 ? 0 : (-e >= c ? 1 : -e/c));
            }
        }
        else if (t < 0) {
            // Region 5

            s = (d >= 0 ? 0 : (-d >= a ? 1 : -d/a));
            t = 0;
        }
        else {
            // Region 0

            double invDet = 1.0/det;
            s *= invDet;
            t *= invDet;
        }
    }
    else {
        if (s < 0) {
            // Region 2

            double temp0 = b+d;
            double temp1 = c+e;
            if (temp1 > temp0) {
                double numer = temp1-temp0;
                double denom = a-2*b+c;
                s = (numer >= denom ? 1 : numer/denom);
                t = 1-s;
            }
            else {
                s = 0;
                t = (temp1 <= 0 ? 1 : (e >= 0 ? 0 : -e/c));
            }
        }
        else if (t < 0) {
            // Region 6

            double temp0 = b+e;
            double temp1 = a+d;
            if (temp1 > temp0) {
                double numer = temp1-temp0;
                double denom = a-2*b+c;
                t = (numer >= denom ? 1 : numer/denom);
                s = 1-t;
            }
            else {
                s = (temp1 <= 0 ? 1 : (e >= 0 ? 0 : -d/a));
                t = 0;
            }
        }
        else {
            // Region 1

            double numer = c+e-b-d;
            if (numer <= 0)
                s = 0;
            else {
                double denom = a-2*b+c;
                s = (numer >= denom ? 1 : numer/denom);
            }
            t = 1-s;
        }
    }
    double dx = x-vert1.x-s*e0x-t*e1x;
    double dy = y-vert1.y-s*e0y-t*e1y;
    double dz = z-vert1.z-s*e0z-t*e1z;
    return dx*dx + dy*dy + dz*dz;
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
