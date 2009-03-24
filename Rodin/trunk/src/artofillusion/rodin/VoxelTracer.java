/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.math.*;

public class VoxelTracer
{
  private VoxelObject obj;
  private int flags[];
  private int minx, maxx, miny, maxy, minz, maxz;
  private int width;

  private static final int vertexOffset[][] = new int[][]
  {
          {0, 0, 0},{1, 0, 0},{1, 1, 0},{0, 1, 0},
          {0, 0, 1},{1, 0, 1},{1, 1, 1},{0, 1, 1}
  };

  public VoxelTracer(VoxelObject obj)
  {
    this.obj = obj;
    initialize();
  }

  private void initialize()
  {
    width = obj.getVoxels().getWidth()-1;
    flags = new int[(width*width*width+31)/32];
    findBounds();
    updateFlags(minx, maxx, miny, maxy, minz, maxz);
  }

  private void findBounds()
  {
    int bounds[] = obj.getVoxels().findDataBounds();
    minx = Math.max(0, bounds[0]-1);
    maxx = Math.min(width-1, bounds[1]);
    miny = Math.max(0, bounds[2]-1);
    maxy = Math.min(width-1, bounds[3]);
    minz = Math.max(0, bounds[4]-1);
    maxz = Math.min(width-1, bounds[5]);
  }

  public void updateFlags(int fromx, int tox, int fromy, int toy, int fromz, int toz)
  {
    VoxelOctree voxels = obj.getVoxels();
    if (width != voxels.getWidth()-1)
    {
      initialize();
      return;
    }
    findBounds();
    float cutoff = 0.5f;
    float values[] = new float[8];
    if (fromx < 0)
      fromx = 0;
    if (fromy < 0)
      fromy = 0;
    if (fromz < 0)
      fromz = 0;
    if (tox >= width)
      tox = width-1;
    if (toy >= width)
      toy = width-1;
    if (toz >= width)
      toz = width-1;
    for (int i = fromx; i <= tox; i++)
    {
      for (int j = fromy; j <= toy; j++)
      {
        for (int k = fromz; k <= toz; k++)
        {
          int numBelow = 0;
          for (int corner = 0; corner < 8; corner++)
          {
            if (corner < 4 && k > 0)
              values[corner] = values[corner+4]; // We just looked this value up for the last cell.
            else
              values[corner] = voxels.getValue(i+vertexOffset[corner][0], j+vertexOffset[corner][1], k+vertexOffset[corner][2]);
            if (values[corner] < cutoff)
              numBelow++;
          }
          int index = i*width*width+j*width+k;
          if (numBelow != 0 && numBelow != 8)
            flags[index/32] |= 1<<(index%32);
          else
            flags[index/32] &= 0xFFFFFFFF-(1<<(index%32));
        }
      }
    }
  }

  public double findRayIntersection(Vec3 origin, Vec3 direction, Vec3 normal)
  {
    double scale = width/obj.getScale();
    double ox = origin.x*scale+0.5*width;
    double oy = origin.y*scale+0.5*width;
    double oz = origin.z*scale+0.5*width;
    double dx = direction.x;
    double dy = direction.y;
    double dz = direction.z;

    // Find the intersection of the ray with the bounding box.

    double mint = -Double.MAX_VALUE;
    double maxt = Double.MAX_VALUE;
    if (dx == 0.0)
    {
      if (ox < minx || ox > maxx)
        return 0.0;
    }
    else
    {
      double t1 = (minx-ox)/dx;
      double t2 = (maxx-ox)/dx;
      if (t1 < t2)
      {
        if (t1 > mint)
          mint = t1;
        if (t2 < maxt)
          maxt = t2;
      }
      else
      {
        if (t2 > mint)
          mint = t2;
        if (t1 < maxt)
          maxt = t1;
      }
      if (mint > maxt || maxt < 0.0)
        return 0.0;
    }
    if (dy == 0.0)
    {
      if (oy < miny || oy > maxy)
        return 0.0;
    }
    else
    {
      double t1 = (miny-oy)/dy;
      double t2 = (maxy-oy)/dy;
      if (t1 < t2)
      {
        if (t1 > mint)
          mint = t1;
        if (t2 < maxt)
          maxt = t2;
      }
      else
      {
        if (t2 > mint)
          mint = t2;
        if (t1 < maxt)
          maxt = t1;
      }
      if (mint > maxt || maxt < 0.0)
        return 0.0;
    }
    if (dz == 0.0)
    {
      if (oz < minz || oz > maxz)
        return 0.0;
    }
    else
    {
      double t1 = (minz-oz)/dz;
      double t2 = (maxz-oz)/dz;
      if (t1 < t2)
      {
        if (t1 > mint)
          mint = t1;
        if (t2 < maxt)
          maxt = t2;
      }
      else
      {
        if (t2 > mint)
          mint = t2;
        if (t1 < maxt)
          maxt = t1;
      }
      if (mint > maxt || maxt < 0.0)
        return 0.0;
    }
    if (mint < 0.0)
      mint = 0.0;

    // Find the first voxel and initialize variables.

    double xpos = ox+mint*dx;
    double ypos = oy+mint*dy;
    double zpos = oz+mint*dz;
    int x = Math.max(Math.min((int) xpos, maxx), minx);
    int y = Math.max(Math.min((int) ypos, maxy), miny);
    int z = Math.max(Math.min((int) zpos, maxz), minz);
    int stepx, stepy, stepz;
    int finalx, finaly, finalz;
    double tdeltax, tdeltay, tdeltaz;
    double tmaxx, tmaxy, tmaxz;
    if (dx < 0.0)
    {
      stepx = -1;
      finalx = -1;
      tdeltax = -1.0/dx;
      tmaxx = (x-ox)/dx;
    }
    else if (dx > 0.0)
    {
      stepx = 1;
      finalx = maxx+1;
      tdeltax = 1.0/dx;
      tmaxx = (x+1-ox)/dx;
    }
    else
    {
      stepx = 0;
      finalx = 0;
      tdeltax = 0.0;
      tmaxx = Double.MAX_VALUE;
    }
    if (dy < 0.0)
    {
      stepy = -1;
      finaly = -1;
      tdeltay = -1.0/dy;
      tmaxy = (y-oy)/dy;
    }
    else if (dy > 0.0)
    {
      stepy = 1;
      finaly = maxy+1;
      tdeltay = 1.0/dy;
      tmaxy = (y+1-oy)/dy;
    }
    else
    {
      stepy = 0;
      finaly = 0;
      tdeltay = 0.0;
      tmaxy = Double.MAX_VALUE;
    }
    if (dz < 0.0)
    {
      stepz = -1;
      finalz = -1;
      tdeltaz = -1.0/dz;
      tmaxz = (z-oz)/dz;
    }
    else if (dz > 0.0)
    {
      stepz = 1;
      finalz = maxz+1;
      tdeltaz = 1.0/dz;
      tmaxz = (z+1-oz)/dz;
    }
    else
    {
      stepz = 0;
      finalz = 0;
      tdeltaz = 0.0;
      tmaxz = Double.MAX_VALUE;
    }

    // Step through the voxels, looking for intersections.

    VoxelOctree voxels = obj.getVoxels();
    float values[] = new float[8];
    while (true)
    {
      int index = x*width*width+y*width+z;
      if ((flags[index/32]&(1<<(index%32))) != 0)
      {
        // There is a piece of the surface in this voxel, so see if the ray intersects it.
        // First find the values of t at which the ray enters and exits it.
        
        double tenter = -Double.MAX_VALUE;
        double texit = Double.MAX_VALUE;
        if (dx != 0.0)
        {
          double t1 = (x-ox)/dx;
          double t2 = (x+1-ox)/dx;
          if (t1 < t2)
          {
            if (t1 > tenter)
              tenter = t1;
            if (t2 < texit)
              texit = t2;
          }
          else
          {
            if (t2 > tenter)
              tenter = t2;
            if (t1 < texit)
              texit = t1;
          }
        }
        if (dy != 0.0)
        {
          double t1 = (y-oy)/dy;
          double t2 = (y+1-oy)/dy;
          if (t1 < t2)
          {
            if (t1 > tenter)
              tenter = t1;
            if (t2 < texit)
              texit = t2;
          }
          else
          {
            if (t2 > tenter)
              tenter = t2;
            if (t1 < texit)
              texit = t1;
          }
        }
        if (dz != 0.0)
        {
          double t1 = (z-oz)/dz;
          double t2 = (z+1-oz)/dz;
          if (t1 < t2)
          {
            if (t1 > tenter)
              tenter = t1;
            if (t2 < texit)
              texit = t2;
          }
          else
          {
            if (t2 > tenter)
              tenter = t2;
            if (t1 < texit)
              texit = t1;
          }
        }
        if (tenter < 0.0)
            continue; // Ignore intersections in the voxel containing the origin.

        // Look up the values at the eight corners of the voxel.

        values[0] = voxels.getValue(x, y, z);
        values[1] = voxels.getValue(x, y, z+1);
        values[2] = voxels.getValue(x, y+1, z);
        values[3] = voxels.getValue(x, y+1, z+1);
        values[4] = voxels.getValue(x+1, y, z);
        values[5] = voxels.getValue(x+1, y, z+1);
        values[6] = voxels.getValue(x+1, y+1, z);
        values[7] = voxels.getValue(x+1, y+1, z+1);

        // Find the positions within the voxel where the ray enters and exits.

        double xenter = ox+dx*tenter-x;
        double yenter = oy+dy*tenter-y;
        double zenter = oz+dz*tenter-z;
        double xexit = ox+dx*texit-x;
        double yexit = oy+dy*texit-y;
        double zexit = oz+dz*texit-z;

        // Interpolate the find the values at those points.

        double enterValue = values[0]*(1.0-xenter)*(1.0-yenter)*(1.0-zenter)
                           +values[1]*(1.0-xenter)*(1.0-yenter)*zenter
                           +values[2]*(1.0-xenter)*yenter*(1.0-zenter)
                           +values[3]*(1.0-xenter)*yenter*zenter
                           +values[4]*xenter*(1.0-yenter)*(1.0-zenter)
                           +values[5]*xenter*(1.0-yenter)*zenter
                           +values[6]*xenter*yenter*(1.0-zenter)
                           +values[7]*xenter*yenter*zenter;
        double exitValue = values[0]*(1.0-xexit)*(1.0-yexit)*(1.0-zexit)
                           +values[1]*(1.0-xexit)*(1.0-yexit)*zexit
                           +values[2]*(1.0-xexit)*yexit*(1.0-zexit)
                           +values[3]*(1.0-xexit)*yexit*zexit
                           +values[4]*xexit*(1.0-yexit)*(1.0-zexit)
                           +values[5]*xexit*(1.0-yexit)*zexit
                           +values[6]*xexit*yexit*(1.0-zexit)
                           +values[7]*xexit*yexit*zexit;
        if ((enterValue > 0.5 && exitValue <= 0.5) || (enterValue <= 0.5 && exitValue > 0.5))
        {
          // Find the intersection point.

          double weight1 = Math.abs(exitValue-0.5);
          double weight2 = Math.abs(enterValue-0.5);
          double d = 1.0/(weight1+weight2);
          weight1 *= d;
          weight2 *= d;
          double tintersect = (tenter*weight1 + texit*weight2)/scale;
          if (normal != null)
          {
            normal.set(values[0]+values[1]+values[2]+values[3]-values[4]-values[5]-values[6]-values[7],
                       values[0]+values[1]-values[2]-values[3]+values[4]+values[5]-values[6]-values[7],
                       values[0]-values[1]+values[2]-values[3]+values[4]-values[5]+values[6]-values[7]);
            normal.normalize();
          }
          return tintersect;
        }
      }

      // Advance to the next voxel.

      if (tmaxx < tmaxy)
      {
        if (tmaxx < tmaxz)
        {
          x += stepx;
          if (x == finalx)
            return 0.0;
          tmaxx += tdeltax;
        }
        else
        {
          z += stepz;
          if (z == finalz)
            return 0.0;
          tmaxz += tdeltaz;
        }
      }
      else
      {
        if (tmaxy < tmaxz)
        {
          y += stepy;
          if (y == finaly)
            return 0.0;
          tmaxy += tdeltay;
        }
        else
        {
          z += stepz;
          if (z == finalz)
            return 0.0;
          tmaxz += tdeltaz;
        }
      }      
    }
  }
}
