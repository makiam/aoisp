/* Copyright (C) 2009-2010 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.rodin;

import artofillusion.*;
import artofillusion.object.*;
import artofillusion.ui.*;
import buoy.widget.*;

public class CreateVoxelsTool implements ModellingTool
{
  public String getName()
  {
    return "Convert to Voxel Object...";
  }

  public void commandSelected(LayoutWindow window)
  {
    ObjectInfo obj = window.getSelectedObjects().iterator().next();
    ValueField errorField = new ValueField(0.01, ValueField.POSITIVE);
    ComponentsDialog dlg = new ComponentsDialog(window, Translate.text("Convert to Voxel Object"),
        new Widget[] {errorField}, new String [] {Translate.text("Voxel Size")});
    if (!dlg.clickedOk())
      return;
    UndoRecord undo = new UndoRecord(window, false, UndoRecord.COPY_OBJECT_INFO, new Object [] {obj, obj.duplicate()});
    VoxelObject voxelObject = VoxelObjectConverter.convertObject(obj, errorField.getValue());
    window.getScene().replaceObject(obj.getObject(), voxelObject, undo);
    window.setUndoRecord(undo);
    window.updateImage();
    window.updateMenus();
  }
}
