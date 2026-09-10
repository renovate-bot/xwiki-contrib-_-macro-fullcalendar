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
package org.xwiki.fullcalendar.internal.util;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.fullcalendar.model.CalendarEvent;
import org.xwiki.stability.Unstable;

import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;

/**
 * Helper class for processing a {@link VEvent} to a {@link CalendarEvent}.
 *
 * @version $Id$
 * @since 2.5.0
 */
@Unstable
@Component(roles = EventProcessor.class)
@Singleton
public class EventProcessor
{
    private static final String T_VALUE = "T";

    @Inject
    private DateProcessor dateProcessor;

    /**
     * Get the start and end dates from a {@link VEvent} and set them to a {@link CalendarEvent}.
     *
     * @param event the source event
     * @param jsonMap the target event
     * @param zoneId {@link ZoneId}
     * @return {@code true} if the dates were successfully set, or {@code false} otherwise.
     */
    public boolean addEventPeriod(VEvent event, CalendarEvent jsonMap, ZoneId zoneId)
    {
        DtStart<?> startDate = event.getDateTimeStart();
        DtEnd<?> endDate = event.getDateTimeEnd();
        if (startDate == null) {
            return false;
        }
        // If either the start or end value has a "T" as part of the ISO8601 date string, allDay will become
        // false. Otherwise, it will be true.
        boolean allDay =
            !(startDate.getValue().contains(T_VALUE) || (endDate != null && endDate.getValue().contains(T_VALUE)));
        jsonMap.setAllDay(allDay);
        jsonMap.setRecurrent(0);
        // ZoneId to interpret LocalDate / LocalDateTime (fallback to system default)
        Object startObj = startDate.getDate();
        Date startUtil = dateProcessor.toUtilDate(startObj, zoneId);
        jsonMap.setStart(startUtil);
        if (endDate != null) {
            Object endObj = endDate.getDate();
            Date endUtil = dateProcessor.toUtilDate(endObj, zoneId);
            jsonMap.setEnd(endUtil);
        } else {
            jsonMap.setEnd(Date.from(startUtil.toInstant().atZone(zoneId).plusDays(1).toInstant()));
        }
        return true;
    }

    /**
     * Set the needed properties of a {@link CalendarEvent} from a {@link VEvent}.
     *
     * @param jsonMap the target event
     * @param event the source event
     */
    public void addBasicEventProperties(CalendarEvent jsonMap, VEvent event)
    {
        jsonMap.setId(event.getUid().isPresent() ? event.getUid().get().getValue() : "");
        jsonMap.setTitle(event.getSummary() == null ? "" : event.getSummary().getValue());

        // Non-standard fields in each Event Object. FullCalendar will not modify or delete these fields.
        jsonMap.setDescription(event.getDescription() == null ? "" : event.getDescription().getValue());
        jsonMap.setLocation(event.getLocation() == null ? "" : event.getLocation().getValue());
        jsonMap.setStatus(event.getStatus() == null ? "" : event.getStatus().getValue());
        Optional<Property> colorOptional = event.getProperty("COLOR");
        colorOptional.ifPresent(property -> jsonMap.setColor(property.getValue()));
        Map<String, Object> properties = new HashMap<>();
        for (Property property : event.getPropertyList().getAll()) {
            // Keep custom properties as metadata so they are not lost during serialization.
            if (property.getName().startsWith("X-")) {
                properties.put(property.getName(), property.getValue());
            }
        }
        jsonMap.setMeta(properties);
    }
}
