/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
/*
 * Lays out the macro editor of the OpenProject work package macros: the Work Packages tab comes first and is active,
 * and the instance is displayed on top of it when the user has to choose the connection (no default connection and
 * not exactly one connection), the Connection tab being then hidden. In the macro editor of all the OpenProject
 * macros, the fields that the pickers enhance are hidden from the assistive technologies (see
 * macroEditorLayout.js#hideEnhancedFields).
 */
(function () {
  const getResourceURL = function (resource) {
    return new XWiki.Document(XWiki.Model.resolve('Main.WebHome', XWiki.EntityType.DOCUMENT))
      .getURL('jsx', 'resource=' + resource);
  };
  require.config({
    paths: {
      'projectmanagement-macro-editor-layout': getResourceURL('js/projectmanagement/macro/macroEditorLayout.js'),
      'openproject-connection': getResourceURL('openproject/js/connection.js')
    }
  });
})();
require(['projectmanagement-macro-editor-layout', 'openproject-connection'], function (layout, connection) {
  const workPackageMacros = ['openproject', 'openprojectchart', 'openprojectcalendar'];
  const layOut = function (editor) {
    const instanceInput = editor.find(':input[name="instance"]');
    layout.mergeGroupTabs(editor, instanceInput.attr('data-other-parameters-label'));
    if (instanceInput.length && connection.isConnectionChoiceNeeded(connection.getConnections(instanceInput))) {
      layout.moveParameterToTop(editor, 'instance', 'filters');
    }
    layout.activateGroup(editor, 'filters');
  };
  // The script is loaded by the instance field.
  layout.onMacroEditor(layout.forMacros(workPackageMacros), layOut);
  // The macro editor of Create Work Package, which has no instance field, does the same (see createWorkPackage.js).
  layout.onMacroEditor(editor => layout.getMacroId(editor.attr('data-macroId')).startsWith('openproject'),
    layout.hideEnhancedFields);
});
