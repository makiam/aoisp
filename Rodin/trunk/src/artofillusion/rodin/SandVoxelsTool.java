/* Copyright (C) 2010 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.ui.*;
import artofillusion.math.*;

public class SandVoxelsTool extends EditVoxelsTool
{
  public SandVoxelsTool(EditingWindow win)
  {
    super(win);
    initButton("movePoints");
  }

  protected void computePadding(double radius)
  {
    padding = Math.max(1, (int) Math.round(radius/5));
  }

  protected float computeNewValue(int x, int y, int z, Vec3 dir)
  {
    float sum = 0;
    int sumWeights = 0;
    float paddingSqr = padding*padding;
    for (int i = -padding; i <= padding; i++)
      for (int j = -padding; j <= padding; j++)
        for (int k = -padding; k <= padding; k++)
        {
          float weight = i*i+j*j+k*k;
          if (weight <= paddingSqr)
          {
            int index = (x+i)*width*width + (y+j)*width + z+k;
            sum += values[index];
            sumWeights += 1;
          }
        }
    return Math.min(values[x*width*width+y*width+z], sum/sumWeights);
  }
}