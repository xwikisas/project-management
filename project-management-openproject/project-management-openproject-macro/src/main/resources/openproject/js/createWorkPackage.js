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
require.config({
  paths: {
    'create-work-package-utils': new XWiki.Document(
      new XWiki.Model.resolve(
        'Main.WebHome',
        XWiki.EntityType.DOCUMENT
      )
    )
    .getURL('jsx', 'resource=js/openproject/createWorkPackagesUtils.js&minify=false')
  }
});

require(["jquery", "create-work-package-utils"], function ($, createWpUtils) {
  function initializeConnectionIfOnlyOneAvailable() {
    var conn = $("#op-connection");

    if (conn.find("option").length === 1) {
      conn.prop("selectedIndex", 0).prop("disabled", true);
      createWpUtils.loadProjects(
        "#op-connection",
        "#op-project",
        "#op-project-container",
        "#incorrect-token-create-work-package"
      );
    }
  }

  async function onProjectChange() {
    const connection = $("#op-connection").val();
    const requestBody = { project: $("#op-project").val() };

    try {
      const response = await createWpUtils.createWorkPackagesRequest(connection, requestBody);
      const container = $("#dynamic-fields-container");
      container.empty().addClass("hidden");

      Object.entries(response).forEach(([key, value]) => {
        const id = `wp-${key}-0`;
        const inputClass = `wp-${key}`;
        const input = createWpUtils.createInput(id, key, inputClass, value);
        container.append(input);
      });

      container.removeClass("hidden");
    } catch (err) {
      createWpUtils.notify("An error occurred while trying to create the inputs!", "error");
    }
  }

  async function onMacroSubmit(element) {
    const modal = element.closest('.macro-editor-modal');

    if (!isFormValid()) {
      createWpUtils.notify("Please fill in all required fields!", "error");
      return;
    }

    const connection = $("#op-connection").val();

    if (!connection) {
      createWpUtils.notify("Please select a connection!", "error");
      return;
    }

    const project = $("#op-project").val();

    if (!project) {
      createWpUtils.notify("Please select a project!", "error");
      return;
    }

    const payload = {...createWpUtils.buildPayload($("#dynamic-fields-container")), 'project': project};

    try {
      const workPackage = await createWpUtils.createWorkPackagesRequest(connection, payload);

      if (!CKEDITOR || !CKEDITOR.instances || !CKEDITOR.instances.content) {
      createWpUtils.notify("Work package created successfully! Please insert it manually as CKEditor instance was not found.", "done");
      return;
    }

    modal.modal('hide');

    CKEDITOR.instances.content.execCommand('xwiki-macro-insert', {
      name: 'openproject',
      inline: 'enforce',
      parameters: {
        instance: connection,
        identifier: workPackage.id,
        workItemsDisplayer: "workItemInline"
      }
    });
    createWpUtils.notify("Work package created and inserted successfully!", "done");
    } catch (err) {
      createWpUtils.notify("An error occurred while trying to create the work package!", "error");
    }
  }

  function isFormValid() {
		const requiredFields = $("#dynamic-fields-container").find("input[required], select[required], textarea[required]");

		if (requiredFields.length === 0) {
			return true;
		}

		let isValid = true;

		requiredFields.each(function() {
			const field = $(this);
			const value = field.val();

			if (value == null || String(value).trim() === "") {
				isValid = false;
			}
		});

		return isValid;
	}

  $(document).on("change", "#op-project", function () {
    onProjectChange();
  });

  $(document).on('click', '.macro-editor-modal .btn-primary', async function (e) {
    const modal = $(this).closest('.macro-editor-modal');

    if (!modal.find('[data-macroid="openproject-create-work-package/xwiki/2.1"]').length) {
      return;
		}

		e.preventDefault();
    await onMacroSubmit($(this));
  });

  $(document).on("change", "#op-connection", function () {
    $("#dynamic-fields-container").empty().addClass("hidden");
    createWpUtils.loadProjects(
      "#op-connection",
      "#op-project",
      "#op-project-container",
      "#incorrect-token-create-work-package"
    );
  });

  $(document).on("show.bs.modal", ".macro-editor-modal", function () {
	  initializeConnectionIfOnlyOneAvailable();
  });

	initializeConnectionIfOnlyOneAvailable();
});
