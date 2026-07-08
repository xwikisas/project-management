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
define("openproject.livedata.action.view", {
    prefix: "openproject.livedata.action.view.",
    keys: [
        "edit"
    ],
});
require(['jquery', "xwiki-l10n!openproject.livedata.action.view"], function ($, l10n) {
    let addEventClass = function (element, obj) {
        const type = obj.meta.type.replace(/\s+/g, '');
        $(element).addClass(`openproject-color-type-${type}`);
    };

    $('.openproject-calendar-macro').each(function () {
        let instance = $(this).data('instance');
        if (!instance) {
            console.warn('The OpenProject calendar is missing an instance. Click events wont be registered.');
            return;
        }
        let calendarId = $(this).find('.xwiki-fullcalendar').attr('id');
        if (!calendarId) {
            console.warn('The OpenProject calendar is missing an id. Click events wont be registered.')
            return;
        }
        document.addEventListener('op-edit-success', function(event) {
          const detail = event.detail || {};
          if (instance !== detail.connection) {
            return;
          }
          $('#' + calendarId).fullCalendar('refetchEvents');
        });
        const eventName = 'xwiki:fullcalendar:' + calendarId + ':eventrendering';
        $(document).on(eventName, function(e, data) {
            let element = data.eventElement;
            let obj = data.eventObj;
            if (!obj.id) {
                new XWiki.widgets.Notification('Event is missing an id.', 'error');
                return;
            }
            if (obj.meta && obj.meta['entity-type'] !== 'workItem') {
                return;
            }
            addEventClass(element, obj);
            element.on('click', function (e) {
                e.preventDefault();
                document.dispatchEvent(new CustomEvent('op-view-requested', {
                    detail: {
                        workPackageId: obj.id,
                        connection: instance,
                        resolve: function() {
                            $('#openproject-item-view-modal-edit-button').remove();
                            let editButton = $('<button></button>')
                                .addClass('btn btn-default')
                                .attr('id', 'openproject-item-view-modal-edit-button')
                                .text(l10n.get('edit'))
                                .on('click', function(e) {
                                    document.dispatchEvent(new CustomEvent('op-edit-requested', {
                                        detail: {
                                            workPackageId: obj.id,
                                            connection: instance
                                        }
                                    }));
                                    $('#openproject-item-view-modal').modal('hide');
                                })
                                .appendTo($('#openproject-item-view-modal .modal-footer'));
                        }
                    }
                }));
            });
        });
    });

    // The active class is not removed when the color picker is closed, so we need to
    // detect which input was clicked and use that for positioning.
    let currentActiveInput = null;
    document.addEventListener('mousedown', function(event) {
        const clickedInput = event.target.closest('.color-picker');
        if (clickedInput) {
            currentActiveInput = clickedInput;
        }
    }, true);

    const colpickObserver = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            const target = mutation.target;
            if (target.classList.contains('colpick') && target.style.display !== 'none') {
                if (!currentActiveInput) {
                    return;
                }
                colpickObserver.disconnect();
                const pickerRect = target.getBoundingClientRect();
                if (pickerRect.bottom > window.innerHeight) {
                    const inputRect = currentActiveInput.getBoundingClientRect();

                    // Calculate the new Y position (input top edge minus the picker height)
                    const newTop = inputRect.top + window.scrollY - pickerRect.height - 5;
                    target.style.setProperty('top', `${newTop}px`, 'important');
                }
                startObserving();
            }
        });
    });

    let startObserving = function() {
        colpickObserver.observe(document.body, {
            attributes: true,
            subtree: true,
            attributeFilter: ['style']
        });
    }
    startObserving();
});
