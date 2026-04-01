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
  $(document).ready(function () {
    const baseUrl = `${XWiki.contextPath}/rest/wikis/${XWiki.currentWiki}/openproject/instance/`;

		function resetDependentFields() {
      $("#op-project").empty().prop("disabled", false);
      $("#dynamic-fields-container").empty().addClass("hidden");
      $("#incorrect-token-create-work-package").addClass("hidden");

      $(".macro-parameter[data-id='OPRequest']")
        .find("input[name='opRequest']")
        .val("");
    }

		$(document).on("shown.bs.modal", ".modal", function () {
      if ($(this).find('[data-macroid="openproject-create-work-package/xwiki/2.1"]').length) {
        resetDependentFields();
        initializeConnectionIfOnlyOneAvailable();
      }
    });

    function initializeConnectionIfOnlyOneAvailable() {
      const connectionSelect = $("#op-connection");

      if (connectionSelect.find("option").length === 1) {
        connectionSelect.prop("selectedIndex", 0);
        connectionSelect.prop("disabled", true);
        connectionSelect.trigger("change");
      }

			const macroParameter = $(".macro-parameter[data-id='OPRequest']");
			const fieldContainer = macroParameter.find(".macro-parameter-field");
			fieldContainer.append(
        $("<input>", {
          type: "hidden",
          name: "opRequest",
          value: ""
        })
      );
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
      console.log("Load projects");
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
          projectSelect.prop("disabled", true).trigger("change");
        } else {
          projectSelect.prop("disabled", false);
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

    function updateHiddenOpRequest() {
     const $macroParameter = $(".macro-parameter[data-id='OPRequest']");
      const $fieldContainer = $macroParameter.find(".macro-parameter-field");
      if ($fieldContainer.length === 0) return;

      const $dynamicFields = $("#dynamic-fields-container");
      const payload = {
          ...buildPayloadFrom($dynamicFields),
          project: $("#op-project").val(),
          connection: $("#op-connection").val()
      };

      $fieldContainer.find("input[name='opRequest']").val(JSON.stringify(payload));
    }

    $(document).on("input change", "#dynamic-fields-container input, #dynamic-fields-container select, #dynamic-fields-container textarea", updateHiddenOpRequest);
    $("#op-project, #op-connection").on("change", updateHiddenOpRequest);

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
        updateHiddenOpRequest(); // update hidden imediat după creare
      } catch (err) {
        notify("An error occurred while trying to create the inputs!", "error");
      }
    }

    $("#op-project").off("change").on("change", onProjectChange);

    $("#op-connection").on("change", function () {
      console.log("Connection changed, loading projects...");
      loadProjects(
        "#op-connection",
        "#op-project",
        "#op-project-container",
        "#incorrect-token-create-work-package"
      );
    });

    initializeConnectionIfOnlyOneAvailable();
    updateHiddenOpRequest();
  });
});