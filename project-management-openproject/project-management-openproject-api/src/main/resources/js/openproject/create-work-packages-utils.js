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
 define('create-work-package-utils', ['jquery'], function ($) {
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
         text: `Select ${fieldData.label}...`,
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
             fieldData.defaultValue.self.location === value
         })
       );
     });
   }

   let notify = function notify(message, type) {
	   new XWiki.widgets.Notification(message, type);
	 }

	 let createInput = function createInput(id, name, fieldClass, fieldData) {
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

       case "date":
       case "text":
         field = $("<input>", {
           type: fieldData.type,
           id: id,
           name: name,
           class: fieldClass,
           required: fieldData.required,
           placeholder: `Enter ${fieldData.label}...`,
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
           placeholder: `Enter ${fieldData.label}...`,
           value: fieldData.defaultValue?.id || ""
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

    let loadProjects = async function loadProjects(connectionSelectId, projectSelectId, projectContainerId,
 incorrectTokenId) {
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

	 let createWPUtils = {
	   notify: notify,
	   createInput: createInput,
	   buildPayload: buildPayload,
	   loadProjects: loadProjects,
	   createWorkPackagesRequest: createWorkPackagesRequest
	 }

   return createWPUtils;
 });