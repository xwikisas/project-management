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
// Init global event bus.
window.openProjectEvents = window.openProjectEvents || new EventTarget();

define("openproject.createworkpackage.utils", {
  prefix: "openproject.createworkpackage.utils.",
  keys: [
    "selectPlaceholder",
    "inputPlaceholder",
    "selectProjectPlaceholder",
    "loadProjects.error",
  ],
});

define('create-work-package-utils', ['jquery', 'xwiki-l10n!openproject.createworkpackage.utils'], function ($, l10n) {
	const baseUrl = `${XWiki.contextPath}/rest/wikis/${XWiki.currentWiki}/openproject/instance/`;

	function populateSelect(selectElement, fieldData) {
	  if (!fieldData || !fieldData.allowedValues) {
	    return;
	  }

	  selectElement.empty();

	  const hasDefault = !!fieldData.defaultValue;

	  selectElement.append(
	    $("<option>", {
	      value: "",
	      text: l10n.get("selectPlaceholder", fieldData.label),
	      disabled: true,
	      selected: !hasDefault
	    })
	  );

	  fieldData.allowedValues.forEach(option => {
	    const value = option.self.location;
	    selectElement.append(
	      $("<option>", {
	        value: value,
	        text: option.name,
	        selected:
	          hasDefault &&
	          fieldData.defaultValue === value
	      })
	    );
	  });
	}

	let notify = function notify(message, type) {
	  new XWiki.widgets.Notification(message, type);
	}

	let createInput = function createInput(id, name, fieldClass, fieldData) {
	  // Only build an input for actual field definitions. Response metadata (e.g. validationMessage,
	  // which is a plain string) has no "type" and must not be rendered as an input.
	  if (!fieldData || !fieldData.type) {
	    return null;
	  }

	  let field;

	  switch (fieldData.type) {
	    case "select":
	      field = $("<select>", {
	        id: id,
	        name: name,
	        class: fieldClass,
	        required: fieldData.required,
	      });
	      populateSelect(field, fieldData);
	      break;

	    case "selectize":
	      // Endpoint-backed field: render a plain select now and turn it into a selectize autocomplete later,
	      // once it is in the DOM, via initDynamicSelectizeFields. The data-* attributes carry everything that
	      // initialization needs.
	      field = $("<select>", {
	        id: id,
	        name: name,
	        class: fieldClass,
	        required: fieldData.required,
	      });
	      field.attr("data-op-selectize", "true");
	      field.attr("data-endpoint", fieldData.endpoint || "");
	      if (fieldData.defaultValue) {
	        field.attr("data-preselect-value", fieldData.defaultValue);
	        if (fieldData.defaultLabel) {
	          field.attr("data-preselect-label", fieldData.defaultLabel);
	        }
	      }
	      break;

	    case "date":
	    case "text":
	      field = $("<input>", {
	        type: fieldData.type,
	        id: id,
	        name: name,
	        class: fieldClass,
	        required: fieldData.required,
	        placeholder: l10n.get("inputPlaceholder", fieldData.label),
	        value: fieldData.defaultValue || ""
	      });
	      break;

	    default:
	      field = $("<input>", {
	        type: "text",
	        id: id,
	        name: name,
	        class: fieldClass,
	        required: fieldData.required,
	        placeholder: l10n.get("inputPlaceholder", fieldData.label),
	        value: fieldData.defaultValue || ""
	      });
	      break;
	  }

	 const wrapper = $("<div>", {
	   class: "form-group",
	 });

	 wrapper.append(
	   $("<label>", {
	     for: id,
	     text: fieldData.label,
	   })
	 );
	 wrapper.append(field);

	 return wrapper;
	}

	let createWorkPackagesRequest = async function createWorkPackagesRequest(connection, requestBody) {
	  const url = `${baseUrl}${connection}/workPackages/create`;
	  return await $.ajax({
	    method: "POST",
	    contentType: "application/json",
	    url: url,
	    data: JSON.stringify(requestBody),
	  });
	}

	let buildPayload = function buildPayload($container) {
	  const payload = {};
	  $container.find('input[name], select[name], textarea[name]').not('.wp-selected').each(function () {
	    const $field = $(this);
	    const value = $field.val();

	    if (value == null || value === "") {
	      return;
	    }

	    const $card = $field.closest('.work-package-card');
	    const $checkbox = $card.find('.wp-selected');
	    if ($checkbox.length && !$checkbox.is(':checked')) {
	      return;
	    }

	    const key = $field.attr('name').replace(/^wp-/, '');
	    payload[key] = value;
	  });

	  return payload;
	}

	let initProjectPicker = function initProjectPicker(connectionSelectId, projectSelectId, projectContainerId,
	incorrectTokenId, preselected, baseUrl) {
	  const connection = $(connectionSelectId).val();
	  const project = $(projectSelectId);

	  if (!connection) {
	    return;
	  }

	  $(projectContainerId).removeClass("hidden");

	  if (project[0] && project[0].selectize) {
	    project[0].selectize.destroy();
	  }

	  project.empty();

	  let selectizeConfig = {
	    create: false,
	    maxItems: 1,
	    inputClass: "selectize-input form-control",
	    preload: true,
	  };

	  selectizeConfig.load = function (text, callback) {
	    const connection = $(connectionSelectId).val();
	    if (!connection) {
	      return callback([]);
	    }
	    const searchUrl = `${baseUrl}${connection}/workPackages/availableProjects`;
	    const selectize = project[0].selectize;
	    $.getJSON(searchUrl, { search: text })
	      .done(function (results) {
	        if (incorrectTokenId) {
	          $(incorrectTokenId).addClass("hidden");
	        }
	        results.forEach(function (result) {
	          if (!selectize.options[result.value]) {
	            selectize.addOption(result);
	          }
	        });
	        selectize.refreshOptions(false);
	        callback();
	        // Auto-select and lock the project when the instance exposes a single one.
	        if (!text && results.length === 1) {
	          selectize.setValue(results[0].value, false);
	          selectize.disable();
	        }
	      })
	      .fail(function (err) {
	        if (err.status === 409 && incorrectTokenId) {
	          const link = $(`${incorrectTokenId} a`);
	          const url = new URL(link.attr("href"), window.location.origin);
	          url.searchParams.set("connectionName", connection);
	          link.attr("href", url.toString());
	          $(incorrectTokenId).removeClass("hidden");
	          $(projectContainerId).addClass("hidden");
	        } else {
	          notify(l10n.get("loadProjects.error"), "error");
	        }
	        callback([]);
	      });
	  }

	  project.xwikiSelectize(selectizeConfig);

	  applyPreselected(project[0].selectize, preselected);

	  if (window.openProjectEvents) {
	    window.openProjectEvents.dispatchEvent(
	      new CustomEvent('projectsSelectDisplayed', { detail: { element: project } })
	    );
	  }
	}

	let applyPreselected = function applyPreselected(selectize, preselected) {
	  if (!preselected?.value) {
	    return;
	  }
	  if (!selectize.options[preselected.value]) {
	    selectize.addOption(preselected);
	  }
	  selectize.setValue(preselected.value, true);
	}

	let initDynamicSelectizeFields = function initDynamicSelectizeFields(container, connectionSelectId,
	projectHref, baseUrl) {
	  $(container).find("select[data-op-selectize]").each(function () {
	    const select = $(this);
	    const endpoint = select.attr("data-endpoint");
	    const preselectValue = select.attr("data-preselect-value");
	    const preselectLabel = select.attr("data-preselect-label");

	    const preselected = preselectValue
	      ? { value: preselectValue, label: preselectLabel || preselectValue }
	      : null;

	    if (select[0] && select[0].selectize) {
	      select[0].selectize.destroy();
	    }

	    let selectizeConfig = {
	      create: false,
	      maxItems: 1,
	      inputClass: "selectize-input form-control",
	      preload: "focus",
	    };

	    selectizeConfig.load = function (text, callback) {
	      const connection = $(connectionSelectId).val();
	      if (!connection) {
	        return callback([]);
	      }
	      const project = projectHref || "";
	      // The endpoint is a full path under the instance (e.g. "workPackages/availableAssignees" or "suggest/parent"),
	      // so field types living on different resources can all be driven the same way.
	      const searchUrl = `${baseUrl}${connection}/${endpoint}`;
	      const selectize = select[0].selectize;
	      $.getJSON(searchUrl, { project: project, search: text })
	        .done(function (results) {
	          results.forEach(function (result) {
	            if (!selectize.options[result.value]) {
	              selectize.addOption(result);
	            } else if (selectize.options[result.value].label != result.label) {
	              selectize.updateOption(result.value, result);
	            }
	          });
	          selectize.refreshOptions(false);
	          callback();
	        })
	        .fail(function (err) {
	          callback([]);
	        });
	    }

	    select.xwikiSelectize(selectizeConfig);

	    applyPreselected(select[0].selectize, preselected);

	    // Let listeners (e.g. the dashboard autofill) react to a freshly built field, keyed by its name.
	    if (window.openProjectEvents) {
	      window.openProjectEvents.dispatchEvent(
	        new CustomEvent('dynamicSelectizeFieldDisplayed', { detail: { element: select, name: select.attr("name") } })
	      );
	    }
	  });
	}

	let destroySelectize = function destroySelectize(container) {
	  $(container).find("select").each(function () {
	    if (this.selectize) {
	      this.selectize.destroy();
	    }
	  });
	}

	let createWPUtils = {
	  notify: notify,
	  createInput: createInput,
	  buildPayload: buildPayload,
	  initProjectPicker: initProjectPicker,
	  initDynamicSelectizeFields: initDynamicSelectizeFields,
	  destroySelectize: destroySelectize,
	  createWorkPackagesRequest: createWorkPackagesRequest
	}

  return createWPUtils;
});