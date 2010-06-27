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
import buoy.event.*;
import buoy.widget.*;

import java.awt.*;

public class CreateVoxelsTool implements ModellingTool
{
  public String getName()
  {
    return "Convert to Voxel Object...";
  }

  public void commandSelected(final LayoutWindow window)
  {
    final ObjectInfo obj = window.getSelectedObjects().iterator().next();
    final ValueField errorField = new ValueField(0.01, ValueField.POSITIVE);
    ComponentsDialog dlg = new ComponentsDialog(window, Translate.text("Convert to Voxel Object"),
        new Widget[] {errorField}, new String [] {Translate.text("Voxel Size")});
    if (!dlg.clickedOk())
      return;
    final BProgressBar progress = new BProgressBar();
    progress.setShowProgressText(true);
    final BDialog progressDialog = new BDialog(window, false);
    BorderContainer content = new BorderContainer();
    progressDialog.setContent(content);
    content.add(progress, BorderContainer.CENTER);
    BButton cancel = new BButton(Translate.text("Cancel"));
    content.add(cancel, BorderContainer.SOUTH, new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(5, 5, 5, 5), null));
    progressDialog.pack();
    progressDialog.setVisible(true);
    final Thread convertThread = new Thread() {
      @Override
      public void run()
      {
        final UndoRecord undo = new UndoRecord(window, false, UndoRecord.COPY_OBJECT_INFO, new Object [] {obj, obj.duplicate()});
        final VoxelObject voxelObject = VoxelObjectConverter.convertObject(obj, errorField.getValue(), progress);
        final boolean interrupted = Thread.currentThread().isInterrupted();
        EventQueue.invokeLater(new Runnable()
        {
          public void run()
          {
            progressDialog.setVisible(false);
            if (!interrupted)
            {
              window.getScene().replaceObject(obj.getObject(), voxelObject, undo);
              window.setUndoRecord(undo);
              window.updateImage();
              window.updateMenus();
            }
          }
        });
      }
    };
    cancel.addEventLink(CommandEvent.class, new Object() {
      void processEvent()
      {
        convertThread.interrupt();
      }
    });
    convertThread.start();
  }
}
