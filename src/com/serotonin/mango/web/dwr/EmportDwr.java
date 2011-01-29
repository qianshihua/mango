/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.web.dwr;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonObject;
import com.serotonin.json.JsonReader;
import com.serotonin.json.JsonValue;
import com.serotonin.json.JsonWriter;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.CompoundEventDetectorDao;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.db.dao.EventDao;
import com.serotonin.mango.db.dao.MailingListDao;
import com.serotonin.mango.db.dao.MaintenanceEventDao;
import com.serotonin.mango.db.dao.PointLinkDao;
import com.serotonin.mango.db.dao.PublisherDao;
import com.serotonin.mango.db.dao.ScheduledEventDao;
import com.serotonin.mango.db.dao.UserDao;
import com.serotonin.mango.db.dao.ViewDao;
import com.serotonin.mango.db.dao.WatchListDao;
import com.serotonin.mango.util.LocalizableJsonException;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.WatchList;
import com.serotonin.mango.vo.permission.Permissions;
import com.serotonin.mango.web.dwr.beans.ImportTask;
import com.serotonin.web.dwr.DwrResponseI18n;

/**
 * @author Matthew Lohbihler
 */
public class EmportDwr extends BaseDwr {
    public static final String GRAPHICAL_VIEWS = "graphicalViews";
    public static final String EVENT_HANDLERS = "eventHandlers";
    public static final String DATA_SOURCES = "dataSources";
    public static final String DATA_POINTS = "dataPoints";
    public static final String SCHEDULED_EVENTS = "scheduledEvents";
    public static final String COMPOUND_EVENT_DETECTORS = "compoundEventDetectors";
    public static final String POINT_LINKS = "pointLinks";
    public static final String USERS = "users";
    public static final String POINT_HIERARCHY = "pointHierarchy";
    public static final String MAILING_LISTS = "mailingLists";
    public static final String PUBLISHERS = "publishers";
    public static final String WATCH_LISTS = "watchLists";
    public static final String MAINTENANCE_EVENTS = "maintenanceEvents";

    public String createExportData(int prettyIndent, boolean graphicalViews, boolean eventHandlers,
            boolean dataSources, boolean dataPoints, boolean scheduledEvents, boolean compoundEventDetectors,
            boolean pointLinks, boolean users, boolean pointHierarchy, boolean mailingLists, boolean publishers,
            boolean watchLists, boolean maintenanceEvents) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();

        if (graphicalViews)
            data.put(GRAPHICAL_VIEWS, new ViewDao().getViews());
        if (dataSources)
            data.put(DATA_SOURCES, new DataSourceDao().getDataSources());
        if (dataPoints)
            data.put(DATA_POINTS, new DataPointDao().getDataPoints(null));
        if (scheduledEvents)
            data.put(SCHEDULED_EVENTS, new ScheduledEventDao().getScheduledEvents());
        if (compoundEventDetectors)
            data.put(COMPOUND_EVENT_DETECTORS, new CompoundEventDetectorDao().getCompoundEventDetectors());
        if (pointLinks)
            data.put(POINT_LINKS, new PointLinkDao().getPointLinks());
        if (users)
            data.put(USERS, new UserDao().getUsers());
        if (mailingLists)
            data.put(MAILING_LISTS, new MailingListDao().getMailingLists());
        if (publishers)
            data.put(PUBLISHERS, new PublisherDao().getPublishers());
        if (pointHierarchy)
            data.put(POINT_HIERARCHY, new DataPointDao().getPointHierarchy(true).getRoot().getSubfolders());
        if (eventHandlers)
            data.put(EVENT_HANDLERS, new EventDao().getEventHandlers());
        if (watchLists) {
            WatchListDao watchListDao = new WatchListDao();
            List<WatchList> wls = watchListDao.getWatchLists();
            watchListDao.populateWatchlistData(wls);
            data.put(WATCH_LISTS, wls);
        }
        if (maintenanceEvents)
            data.put(MAINTENANCE_EVENTS, new MaintenanceEventDao().getMaintenanceEvents());

        JsonWriter writer = new JsonWriter();
        writer.setPrettyIndent(prettyIndent);
        writer.setPrettyOutput(true);

        try {
            return writer.write(data);
        }
        catch (JsonException e) {
            throw new ShouldNeverHappenException(e);
        }
        catch (IOException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    public DwrResponseI18n importData(String data) {
        DwrResponseI18n response = new DwrResponseI18n();
        ResourceBundle bundle = getResourceBundle();

        User user = Common.getUser();
        Permissions.ensureAdmin(user);

        JsonReader reader = new JsonReader(data);
        try {
            JsonValue value = reader.inflate();
            if (value instanceof JsonObject) {
                JsonObject root = value.toJsonObject();
                ImportTask importTask = new ImportTask(reader, root, bundle, user);
                user.setImportTask(importTask);
                response.addData("importStarted", true);
            }
            else {
                response.addGenericMessage("emport.invalidImportData");
            }
        }
        catch (ClassCastException e) {
            response.addGenericMessage("emport.parseError", e.getMessage());
        }
        catch (LocalizableJsonException e) {
            response.addMessage(e.getMsg());
        }
        catch (JsonException e) {
            response.addGenericMessage("emport.parseError", e.getMessage());
        }

        return response;
    }

    public DwrResponseI18n importUpdate() {
        DwrResponseI18n response;
        User user = Common.getUser();
        ImportTask importTask = user.getImportTask();
        if (importTask != null) {
            response = importTask.getResponse();

            if (importTask.isCancelled()) {
                response.addData("cancelled", true);
                user.setImportTask(null);
            }
            else if (importTask.isCompleted()) {
                response.addData("complete", true);
                user.setImportTask(null);
            }
        }
        else {
            response = new DwrResponseI18n();
            response.addData("noImport", true);
        }
        return response;
    }

    public void importCancel() {
        User user = Common.getUser();
        if (user.getImportTask() != null)
            user.getImportTask().cancel();
    }
}
