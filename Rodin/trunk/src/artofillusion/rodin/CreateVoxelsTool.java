/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.*;
import artofillusion.math.*;

public class CreateVoxelsTool implements ModellingTool
{
  public String getName()
  {
    return "Create Voxel Object";
  }

  public void commandSelected(LayoutWindow window)
  {
    int depth = 6;
    int width = 1<<depth;
    int center = width/2;
    int size = center-5;
    VoxelObject obj = new VoxelObject(depth);
    for (int i = 0; i < width; i++)
      for (int j = 0; j < width; j++)
        for (int k = 0; k < width; k++)
        {
            float radius = (float) Math.sqrt((i-center)*(i-center)+(j-center)*(j-center)+(k-center)*(k-center));
            if (radius < size-3)
              obj.getVoxels().setValue(i, j, k, Byte.MAX_VALUE);
            else if (radius > size-1)
              obj.getVoxels().setValue(i, j, k, Byte.MIN_VALUE);
            else
              obj.getVoxels().setValue(i, j, k, (byte) (Byte.MAX_VALUE*(size-2-radius)));

        }
    window.addObject(obj, new CoordinateSystem(), "Voxel object", null);
    window.updateImage();
    window.updateMenus();
  }
}
