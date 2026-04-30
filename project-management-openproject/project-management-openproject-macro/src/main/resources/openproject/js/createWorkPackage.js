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

define("openproject.createworkpackage.macro", {
  prefix: "openproject.createworkpackage.macro.",
  keys: [
    "notify.fillRequiredFields",
    "notify.selectConnection",
    "notify.selectProject",
    "notify.inputs.error",
    "notify.submit.noCkeditor",
    "notify.submit.success",
    "notify.submit.error",
  ],
});

require(["jquery", "create-work-package-utils", "xwiki-l10n!openproject.createworkpackage.macro"], function ($, createWpUtils, l10n) {
  async function initializeConnectionIfOnlyOneAvailable() {
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
      createWpUtils.notify(l10n.get("notify.inputs.error"), "error");
    }
  }

  async function onMacroSubmit(element) {
    const modal = element.closest('.macro-editor-modal');

    if (!isFormValid()) {
      createWpUtils.notify(l10n.get("notify.fillRequiredFields"), "error");
      return;
    }

    const connection = $("#op-connection").val();

    if (!connection) {
      createWpUtils.notify(l10n.get("notify.selectConnection"), "error");
      return;
    }

    const project = $("#op-project").val();

    if (!project) {
      createWpUtils.notify(l10n.get("notify.selectProject"), "error");
      return;
    }

    const payload = {...createWpUtils.buildPayload($("#dynamic-fields-container")), 'project': project};
    let ckeditorInstance = CKEDITOR && CKEDITOR.instances &&
      (CKEDITOR.instances.content || CKEDITOR.instances.xwikicontent);
    try {
      const workPackage = await createWpUtils.createWorkPackagesRequest(connection, payload);

      if (!ckeditorInstance) {
      createWpUtils.notify(l10n.get("notify.submit.noCkeditor"), "done");
      return;
    }

    modal.modal('hide');

    ckeditorInstance.execCommand('xwiki-macro-insert', {
      name: 'openproject',
      inline: 'enforce',
      parameters: {
        instance: connection,
        identifier: workPackage.id,
        workItemsDisplayer: "workItemInline"
      }
    });
    createWpUtils.notify(l10n.get("notify.submit.success"), "done");
    } catch (err) {
      createWpUtils.notify(l10n.get("notify.submit.error"), "error");
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
