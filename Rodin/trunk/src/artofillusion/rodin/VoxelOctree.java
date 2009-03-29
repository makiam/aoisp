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
  private static final VoxelTreeNode leafNode[];

  static
  {
    leafNode = new VoxelTreeNode[256];
    for (int i = 0; i < leafNode.length; i++)
      leafNode[i] = new VoxelTreeNode((byte) (i+Byte.MIN_VALUE));
  }

  /**
   * Create a VoxelOctree.
   *
   * @param depth    the depth of the octree.  The width of the grid along each dimension
   *                 is 2^depth.
   */

  public VoxelOctree(int depth)
  {
    if (depth < 1)
      throw new IllegalArgumentException("Illegal depth value: "+depth);
    this.depth = depth;
    root = leafNode[0];
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

  public byte getValue(int x, int y, int z)
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
      node = node.children[child];
    }
    return node.value;
  }

  /**
   * Set the value of a point on the grid, specified by its x, y, and z coordinates.
   */

  public void setValue(int x, int y, int z, byte value)
  {
    root = setValue(root, depth, x, y, z, value);
  }

  private VoxelTreeNode setValue(VoxelTreeNode node, int depth, int x, int y, int z, byte value)
  {
    if (depth == 0)
      return leafNode[value-Byte.MIN_VALUE];
    if (node.children == null)
    {
      if (node.value == value)
        return node;
      VoxelTreeNode oldNode = node;
      node = new VoxelTreeNode();
      for (int i = 0; i < node.children.length; i++)
        node.children[i] = leafNode[oldNode.value-Byte.MIN_VALUE];
    }
    depth--;
    int i = x>>depth;
    int j = y>>depth;
    int k = z>>depth;
    int child = (i&1)*4 + (j&1)*2 + (k&1);
    node.children[child] = setValue(node.children[child], depth, x, y, z, value);
    boolean uniform = true;
    for (int m = 0; uniform && m < 8; m++)
      if (node.children[m].children != null || node.children[m].value != value)
        uniform = false;
    if (uniform)
      return leafNode[value-Byte.MIN_VALUE];
    return node;
  }

  /**
   * Find the range of grid points that contain values greater than Byte.MIN_VALUE.
   * It is returned as the array [minx, maxx, miny, maxy, minz, maxz].
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
      if (node.value != Byte.MIN_VALUE)
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
          if (node.value != Byte.MIN_VALUE)
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
      if (node.value != Byte.MIN_VALUE)
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
          if (node.value != Byte.MIN_VALUE)
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
    VoxelOctree copy = new VoxelOctree(depth);
    copy.root = duplicateNode(root);
    return copy; 
  }

  /**
   * Double the size of the grid along each dimension.  This is done symmetrically,
   * such that the old grid values occupy the center of the new grid.
   */

  public void growGrid()
  {
    if (root.children == null)
      return;
    VoxelTreeNode newRoot = new VoxelTreeNode();
    for (int i = 0; i < 8; i++)
    {
      newRoot.children[i] = new VoxelTreeNode();
      for (int j = 0; j < 8; j++)
        newRoot.children[i].children[j] = (j == 7-i ? root.children[i] : leafNode[0]);
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
    if (depth == 1 || root.children == null)
      return;
    if (depth == 2)
    {
      root = leafNode[0];
      depth = 1;
      return;
    }
    VoxelTreeNode newRoot = new VoxelTreeNode();
    for (int i = 0; i < 8; i++)
    {
      if (root.children[i].children == null)
        newRoot.children[i] = root.children[i];
      else
        newRoot.children[i] = root.children[i].children[7-i];
    }
    root = newRoot;
    depth--;
  }

  private static VoxelTreeNode duplicateNode(VoxelTreeNode node)
  {
    if (node.children == null)
      return node;
    VoxelTreeNode copy = new VoxelTreeNode();
    for (int i = 0; i < 8; i++)
      copy.children[i] = duplicateNode(node.children[i]);
    return copy;
  }

  private static class VoxelTreeNode
  {
    public byte value;
    public VoxelTreeNode children[];

    public VoxelTreeNode(byte value)
    {
      this.value = value;
    }

    public VoxelTreeNode()
    {
      children = new VoxelTreeNode[8];
    }
  }
}
