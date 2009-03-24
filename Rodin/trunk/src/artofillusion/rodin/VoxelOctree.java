/* Copyright (C) 2009 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import java.util.*;

/**
 * This class represents a cubic grid, storing a single number at each grid point.  It is
 * implemented using an octree, so regions of constant value are stored very efficiently.
 */

public class VoxelOctree
{
  private VoxelTreeNode root;
  private int depth;

  /**
   * Create a VoxelOctree.
   *
   * @param depth    the depth of the octree.  The width of the grid along each dimension
   *                 is 2^depth.
   * @param value    the initial value to store at every grid point
   */

  public VoxelOctree(int depth, float value)
  {
    if (depth < 1)
      throw new IllegalArgumentException("Illegal depth value: "+depth);
    this.depth = depth;
    root = new VoxelTreeNode(value);
  }

  /**
   * Get the depth of the octree.
   */

  public int getDepth()
  {
    return depth;
  }

  /**
   * Get the width of the grid along each dimension.  This is 2^depth.
   */

  public int getWidth()
  {
    return 1<<depth;
  }

  /**
   * Get the value of a point on the grid, specified by its x, y, and z coordinates.
   */

  public float getValue(int x, int y, int z)
  {
    VoxelTreeNode node = root;
    int currentDepth = depth;
    while (node.children != null)
    {
      currentDepth--;
      int i = x>>currentDepth;
      int j = y>>currentDepth;
      int k = z>>currentDepth;
      int child = (i&1)*4 + (j&1)*2 + (k&1);
      if (node.children[child] == null)
        return node.value;
      node = node.children[child];
    }
    return node.value;
  }

  /**
   * Set the value of a point on the grid, specified by its x, y, and z coordinates.
   */

  public void setValue(int x, int y, int z, float value)
  {
    setValue(root, depth, x, y, z, value);
  }

  private void setValue(VoxelTreeNode node, int depth, int x, int y, int z, float value)
  {
    if (depth == 0)
    {
      node.value = value;
      node.children = null;
      return;
    }
    if (node.children == null)
    {
      if (node.value == value)
        return;
      node.children = new VoxelTreeNode[8];
    }
    depth--;
    int i = x>>depth;
    int j = y>>depth;
    int k = z>>depth;
    int child = (i&1)*4 + (j&1)*2 + (k&1);
    VoxelTreeNode childNode = node.children[child];
    if (childNode == null)
      childNode = node.children[child] = new VoxelTreeNode(node.value);
    setValue(childNode, depth, x, y, z, value);
    if (childNode.children == null && childNode.value == node.value)
      node.children[child] = null;
    boolean uniform = true;
    float firstValue = (node.children[0] == null ? node.value : node.children[0].value);
    for (int m = 0; uniform && m < 8; m++)
    {
      if (node.children[m] != null && node.children[m].children != null)
        uniform = false;
      float childValue = (node.children[m] == null ? node.value : node.children[m].value);
      if (childValue != firstValue)
        uniform = false;
    }
    if (uniform)
    {
      node.value = firstValue;
      node.children = null;
    }
  }

  /**
   * Find the range of grid points that contain non-zero values.  It is returned as
   * the array [minx, maxx, miny, maxy, minz, maxz].
   */

  public int[] findDataBounds()
  {
    int width = 1<<depth;
    int bounds[] = new int[] {width, 0, width, 0, width, 0};
    findLowerBounds(bounds, root, depth-1, 0, 0, 0);
    findUpperBounds(bounds, root, depth-1, width-1, width-1, width-1);
    if (bounds[1] < bounds[0])
      Arrays.fill(bounds, 0);
    return bounds;
  }

  private void findLowerBounds(int bounds[], VoxelTreeNode node, int depth, int x, int y, int z)
  {
    if (node.children == null)
    {
      if (node.value != 0.0f)
      {
        if (x < bounds[0])
          bounds[0] = x;
        if (y < bounds[2])
          bounds[2] = y;
        if (z < bounds[4])
          bounds[4] = z;
      }
      return;
    }
    for (int i = 0; i < 8; i++)
    {
      int childx = ((i&4) == 0 ? x : x+(1<<depth));
      int childy = ((i&2) == 0 ? y : y+(1<<depth));
      int childz = ((i&1) == 0 ? z : z+(1<<depth));
      if (childx < bounds[0] || childy < bounds[2] || childz < bounds[4])
      {
        if (node.children[i] == null)
        {
          if (node.value != 0.0f)
          {
            if (childx < bounds[0])
              bounds[0] = childx;
            if (childy < bounds[2])
              bounds[2] = childy;
            if (childz < bounds[4])
              bounds[4] = childz;
          }
        }
        else
          findLowerBounds(bounds, node.children[i], depth-1, childx, childy, childz);
      }
    }
  }

  private void findUpperBounds(int bounds[], VoxelTreeNode node, int depth, int x, int y, int z)
  {
    if (node.children == null)
    {
      if (node.value != 0.0f)
      {
        if (x > bounds[1])
          bounds[1] = x;
        if (y > bounds[3])
          bounds[3] = y;
        if (z > bounds[5])
          bounds[5] = z;
      }
      return;
    }
    for (int i = 7; i >= 0; i--)
    {
      int childx = ((i&4) == 0 ? x-(1<<depth) : x);
      int childy = ((i&2) == 0 ? y-(1<<depth) : y);
      int childz = ((i&1) == 0 ? z-(1<<depth) : z);
      if (childx > bounds[1] || childy > bounds[3] || childz > bounds[5])
      {
        if (node.children[i] == null)
        {
          if (node.value != 0.0f)
          {
            if (childx > bounds[1])
              bounds[1] = childx;
            if (childy > bounds[3])
              bounds[3] = childy;
            if (childz > bounds[5])
              bounds[5] = childz;
          }
        }
        else
          findUpperBounds(bounds, node.children[i], depth-1, childx, childy, childz);
      }
    }
  }

  /**
   * Create a duplicate of this VoxelOctree.
   */

  public VoxelOctree duplicate()
  {
    VoxelOctree copy = new VoxelOctree(depth, 0.0f);
    copy.root = duplicateNode(root);
    return copy; 
  }

  /**
   * Double the size of the grid along each dimension.  This is done symmetrically,
   * such that the old grid values occupy the center of the new grid.
   *
   * @param value    the value to store at the newly created grid points
   */

  public void growGrid(float value)
  {
    VoxelTreeNode newRoot = new VoxelTreeNode(value);
    newRoot.children = new VoxelTreeNode[8];
    for (int i = 0; i < 8; i++)
    {
      newRoot.children[i] = new VoxelTreeNode(value);
      if (root.children != null && root.children[i] != null)
      {
        newRoot.children[i].children = new VoxelTreeNode[8];
        newRoot.children[i].children[7-i] = root.children[i];
      }
      else if (root.value != value)
      {
        newRoot.children[i].children = new VoxelTreeNode[8];
        newRoot.children[i].children[7-i] = new VoxelTreeNode(root.value);
      }
    }
    root = newRoot;
    depth++;
  }


  /**
   * Halve the size of the grid along each dimension.  This is done symmetrically,
   * such that the new grid contains the values from the center of the old grid.
   */

  public void shrinkGrid()
  {
    if (depth == 1)
      return;
    VoxelTreeNode newRoot = new VoxelTreeNode(root.value);
    if (root.children != null)
    {
      newRoot.children = new VoxelTreeNode[8];
      for (int i = 0; i < 8; i++)
      {
        if (root.children[i] != null)
        {
          if (root.children[i].children == null)
            newRoot.children[i] = root.children[i];
          else
            newRoot.children[i] = root.children[i].children[7-i];
        }
      }
    }
    root = newRoot;
    depth--;
  }

  private static VoxelTreeNode duplicateNode(VoxelTreeNode node)
  {
    VoxelTreeNode copy = new VoxelTreeNode(node.value);
    if (node.children != null)
    {
      copy.children = new VoxelTreeNode[8];
      for (int i = 0; i < 8; i++)
        if (node.children[i] != null)
          copy.children[i] = duplicateNode(node.children[i]);
    }
    return copy;
  }

  private static class VoxelTreeNode
  {
    public float value;
    public VoxelTreeNode children[];

    public VoxelTreeNode(float value)
    {
      this.value = value;
    }
  }
}
