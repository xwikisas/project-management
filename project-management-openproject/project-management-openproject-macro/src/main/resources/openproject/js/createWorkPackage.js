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
require(["jquery"], function ($) {
  const baseUrl = `${XWiki.contextPath}/rest/wikis/${XWiki.currentWiki}/openproject/instance/`;

  function initializeConnectionIfOnlyOneAvailable() {
    var $conn = $("#op-connection");

    if ($conn.find("option").length === 1) {
      $conn.prop("selectedIndex", 0).prop("disabled", true);
      loadProjects(
        "#op-connection",
        "#op-project",
        "#op-project-container",
        "#incorrect-token-create-work-package"
      );
    }
  }

  async function createWorkPackagesRequest(connection, requestBody) {
    const url = `${baseUrl}${connection}/workPackages/create`;
    return await $.ajax({
      method: "POST",
      contentType: "application/json",
      url: url,
      data: JSON.stringify(requestBody),
    });
  }

  async function loadProjects(connectionSelectId, projectSelectId, projectContainerId, incorrectTokenId) {
    const connection = $(connectionSelectId).val();
    const url = `${baseUrl}${connection}/workPackages/availableProjects`;
    try {
      const projects = await $.ajax({
        method: "GET",
        contentType: "application/json",
        url: url,
      });

      const projectSelect = $(projectSelectId);
      projectSelect.empty();

      if (projects.length === 1) {
        projectSelect.append(
          $("<option>", { value: projects[0].self.location, text: projects[0].name, selected: true })
        );
        projectSelect.prop("disabled", true);
        await onProjectChange();
      } else {
        projectSelect.append(
          $("<option>", { value: "", text: "Select project...", disabled: true, selected: true })
        );
        projects.forEach((project) => {
          projectSelect.append(
            $("<option>", { value: project.self.location, text: project.name })
          );
        });
      }

      $(incorrectTokenId).addClass("hidden");
      $(projectContainerId).removeClass("hidden");
    } catch (err) {
      if (err.status === 409) {
        const link = $(`${incorrectTokenId} a`);
        const url = new URL(link.attr("href"), window.location.origin);
        url.searchParams.set("connectionName", connection);
        link.attr("href", url.toString());
        $(incorrectTokenId).removeClass("hidden");
        $(projectContainerId).addClass("hidden");
        return;
      }
      $(connectionSelectId).val("");
      notify("An error occurred while trying to get the project options!", "error");
    }
  }

  function createInput(id, name, fieldClass, fieldData) {
    let field;

    switch (fieldData.type) {
      case "select":
        field = $("<select>", { id, name, class: fieldClass, required: fieldData.required });
        populateSelect(field, fieldData);
        break;
      case "date":
      case "text":
        field = $("<input>", {
          type: fieldData.type,
          id,
          name,
          class: fieldClass,
          required: fieldData.required,
          placeholder: `Enter ${fieldData.label}...`,
          value: fieldData.defaultValue || ""
        });
        break;
      default:
        field = $("<input>", {
          type: "text",
          id,
          name,
          class: fieldClass,
          required: fieldData.required,
          placeholder: `Enter ${fieldData.label}...`,
          value: fieldData.defaultValue?.id || ""
        });
        break;
    }

    const wrapper = $("<div>", { class: "form-group" });
    wrapper.append($("<label>", { for: id, text: fieldData.label }));
    wrapper.append(field);
    return wrapper;
  }

  function populateSelect(selectElement, fieldData) {
    if (!fieldData || !fieldData.allowedValues) return;

    selectElement.empty();
    const hasDefault = !!fieldData.defaultValue;

    selectElement.append(
      $("<option>", { value: "", text: `Select ${fieldData.label}...`, disabled: true, selected: !hasDefault })
    );

    fieldData.allowedValues.forEach(option => {
      const value = option.self.location;
      selectElement.append(
        $("<option>", {
          value,
          text: option.name,
          selected: hasDefault && fieldData.defaultValue.self.location === value
        })
      );
    });
  }

  function buildPayloadFrom($container) {
    const payload = {};
    $container.find('input[name], select[name], textarea[name]').not('.wp-selected').each(function () {
      const $field = $(this);
      const value = $field.val();
      if (!value) return;

      const $card = $field.closest('.work-package-card');
      const $checkbox = $card.find('.wp-selected');
      if ($checkbox.length && !$checkbox.is(':checked')) return;

      const key = $field.attr('name').replace(/^wp-/, '');
      payload[key] = value;
    });
    return payload;
  }

  function notify(message, type) {
    new XWiki.widgets.Notification(message, type);
  }

  async function onProjectChange() {
    const connection = $("#op-connection").val();
    const requestBody = { project: $("#op-project").val() };

    try {
      const response = await createWorkPackagesRequest(connection, requestBody);
      const container = $("#dynamic-fields-container");
      container.empty();

      Object.entries(response).forEach(([key, value]) => {
        const id = `wp-${key}-0`;
        const inputClass = `wp-${key}`;
        const input = createInput(id, key, inputClass, value);
        container.append(input);
      });

      container.removeClass("hidden");
    } catch (err) {
      notify("An error occurred while trying to create the inputs!", "error");
    }
  }

  function isFormValid() {
		const requiredFields = $("#dynamic-fields-container").find("input[required], select[required], textarea[required]");

		if (requiredFields.length === 0) {
			return true;
		}

		let isValid = true;

		requiredFields.each(function() {
			const $field = $(this);
			const value = $field.val();

			if (value == null || String(value).trim() === "") {
				isValid = false;
				$field.trigger("focus");
				return false;
			}
		});

		return isValid;
	}

  $(document).on("change", "#op-project", function () {
    onProjectChange();
  });

  $('.macro-editor-modal .btn-primary').on('click', async function (e) {
    e.preventDefault();

	   if (!isFormValid()) {
	      notify("Please fill in all required fields!", "error");
		    return;
	   }

     const project = $("#op-project").val();
     const payload = {...buildPayloadFrom($("#dynamic-fields-container")), 'project': project};

		 try {
		 const workPackage = await createWorkPackagesRequest($("#op-connection").val(), payload);
		 const connection = $("#op-connection").val();
		 $('.macro-editor-modal').modal('hide');
     CKEDITOR.instances.content.execCommand('xwiki-macro-insert', {
        name: 'openproject',
        inline: 'enforce',
        parameters: {
          instance: connection,
          identifier: workPackage.id,
          workItemsDisplayer: "workItemsSingle"
        }
      });
      notify("Work package created and inserted successfully!", "done");
		} catch (err) {
			return notify("An error occurred while trying to create the work package!", "error");
		}
  });

  $(document).on("change", "#op-connection", function () {
    loadProjects(
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