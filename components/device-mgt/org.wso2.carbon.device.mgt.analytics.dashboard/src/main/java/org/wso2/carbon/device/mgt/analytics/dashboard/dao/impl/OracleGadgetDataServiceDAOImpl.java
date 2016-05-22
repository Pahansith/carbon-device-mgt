/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.device.mgt.analytics.dashboard.dao.impl;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.device.mgt.analytics.dashboard.bean.DetailedDeviceEntry;
import org.wso2.carbon.device.mgt.analytics.dashboard.bean.DeviceCountByGroupEntry;
import org.wso2.carbon.device.mgt.analytics.dashboard.bean.FilterSet;
import org.wso2.carbon.device.mgt.analytics.dashboard.dao.AbstractGadgetDataServiceDAO;
import org.wso2.carbon.device.mgt.analytics.dashboard.dao.GadgetDataServiceDAOConstants;
import org.wso2.carbon.device.mgt.analytics.dashboard.exception.InvalidParameterValueException;
import org.wso2.carbon.device.mgt.common.PaginationResult;
import org.wso2.carbon.device.mgt.core.dao.util.DeviceManagementDAOUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OracleGadgetDataServiceDAOImpl extends AbstractGadgetDataServiceDAO {

    @Override
    public PaginationResult getNonCompliantDeviceCountsByFeatures(int startIndex, int resultCount)
                                                           throws InvalidParameterValueException, SQLException {

        if (startIndex < GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX) {
            throw new InvalidParameterValueException("Start index should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX + " or greater than that.");
        }

        if (resultCount < GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT) {
            throw new InvalidParameterValueException("Result count should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT + " or greater than that.");
        }

        Connection con;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        List<DeviceCountByGroupEntry> filteredNonCompliantDeviceCountsByFeatures = new ArrayList<>();
        int totalRecordsCount = 0;
        try {
            con = this.getConnection();
            String sql = "SELECT * FROM (SELECT ROWNUM offset, rs.* FROM (SELECT FEATURE_CODE, COUNT(DEVICE_ID) " +
                "AS DEVICE_COUNT FROM " + GadgetDataServiceDAOConstants.DatabaseView.DEVICES_VIEW_2 +
                    " WHERE TENANT_ID = ? GROUP BY FEATURE_CODE ORDER BY DEVICE_COUNT DESC) rs) " +
                        "WHERE offset >= ? AND ROWNUM <= ?";

            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tenantId);
            stmt.setInt(2, startIndex);
            stmt.setInt(3, resultCount);

            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            DeviceCountByGroupEntry filteredNonCompliantDeviceCountByFeature;
            while (rs.next()) {
                filteredNonCompliantDeviceCountByFeature = new DeviceCountByGroupEntry();
                filteredNonCompliantDeviceCountByFeature.setGroup(rs.getString("FEATURE_CODE"));
                filteredNonCompliantDeviceCountByFeature.setDisplayNameForGroup(rs.getString("FEATURE_CODE"));
                filteredNonCompliantDeviceCountByFeature.setDeviceCount(rs.getInt("DEVICE_COUNT"));
                filteredNonCompliantDeviceCountsByFeatures.add(filteredNonCompliantDeviceCountByFeature);
            }
            // fetching total records count
            sql = "SELECT COUNT(FEATURE_CODE) AS NON_COMPLIANT_FEATURE_COUNT FROM " +
                "(SELECT DISTINCT FEATURE_CODE FROM " + GadgetDataServiceDAOConstants.DatabaseView.DEVICES_VIEW_2 +
                    " WHERE TENANT_ID = ?) NON_COMPLIANT_FEATURE_CODE";

            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tenantId);

            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            while (rs.next()) {
                totalRecordsCount = rs.getInt("NON_COMPLIANT_FEATURE_COUNT");
            }
        } finally {
            DeviceManagementDAOUtil.cleanupResources(stmt, rs);
        }
        PaginationResult paginationResult = new PaginationResult();
        paginationResult.setData(filteredNonCompliantDeviceCountsByFeatures);
        paginationResult.setRecordsTotal(totalRecordsCount);
        return paginationResult;
    }

    @Override
    public PaginationResult getDevicesWithDetails(FilterSet filterSet, int startIndex, int resultCount)
                                                            throws InvalidParameterValueException, SQLException {

        if (startIndex < GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX) {
            throw new InvalidParameterValueException("Start index should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX + " or greater than that.");
        }

        if (resultCount < GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT) {
            throw new InvalidParameterValueException("Result count should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT + " or greater than that.");
        }

        Map<String, Object> filters = this.extractDatabaseFiltersFromBean(filterSet);

        Connection con;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        List<DetailedDeviceEntry> filteredDevicesWithDetails = new ArrayList<>();
        int totalRecordsCount = 0;
        try {
            con = this.getConnection();
            String sql, advancedSqlFiltering = "";
            // appending filters if exist, to support advanced filtering options
            // [1] appending filter columns, if exist
            if (filters != null && filters.size() > 0) {
                for (String column : filters.keySet()) {
                    advancedSqlFiltering = advancedSqlFiltering + "AND " + column + " = ? ";
                }
            }
            sql = "SELECT * FROM (SELECT ROWNUM offset, rs.* FROM (SELECT DEVICE_ID, DEVICE_IDENTIFICATION, PLATFORM, " +
                "OWNERSHIP, CONNECTIVITY_STATUS FROM " + GadgetDataServiceDAOConstants.DatabaseView.DEVICES_VIEW_1 +
                    " WHERE TENANT_ID = ? " + advancedSqlFiltering + "ORDER BY DEVICE_ID ASC) rs) " +
                        "WHERE offset >= ? AND ROWNUM <= ?";

            stmt = con.prepareStatement(sql);
            // [2] appending filter column values, if exist
            stmt.setInt(1, tenantId);
            if (filters != null && filters.values().size() > 0) {
                int i = 2;
                for (Object value : filters.values()) {
                    if (value instanceof Integer) {
                        stmt.setInt(i, (Integer) value);
                    } else if (value instanceof String) {
                        stmt.setString(i, (String) value);
                    }
                    i++;
                }
                stmt.setInt(i, startIndex);
                stmt.setInt(++i, resultCount);
            } else {
                stmt.setInt(2, startIndex);
                stmt.setInt(3, resultCount);
            }
            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            DetailedDeviceEntry filteredDeviceWithDetails;
            while (rs.next()) {
                filteredDeviceWithDetails = new DetailedDeviceEntry();
                filteredDeviceWithDetails.setDeviceId(rs.getInt("DEVICE_ID"));
                filteredDeviceWithDetails.setDeviceIdentification(rs.getString("DEVICE_IDENTIFICATION"));
                filteredDeviceWithDetails.setPlatform(rs.getString("PLATFORM"));
                filteredDeviceWithDetails.setOwnershipType(rs.getString("OWNERSHIP"));
                filteredDeviceWithDetails.setConnectivityStatus(rs.getString("CONNECTIVITY_STATUS"));
                filteredDevicesWithDetails.add(filteredDeviceWithDetails);
            }

            // fetching total records count
            sql = "SELECT COUNT(DEVICE_ID) AS DEVICE_COUNT FROM " + GadgetDataServiceDAOConstants.
                DatabaseView.DEVICES_VIEW_1 + " WHERE TENANT_ID = ?";

            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tenantId);

            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            while (rs.next()) {
                totalRecordsCount = rs.getInt("DEVICE_COUNT");
            }
        } finally {
            DeviceManagementDAOUtil.cleanupResources(stmt, rs);
        }
        PaginationResult paginationResult = new PaginationResult();
        paginationResult.setData(filteredDevicesWithDetails);
        paginationResult.setRecordsTotal(totalRecordsCount);
        return paginationResult;
    }

    @Override
    public PaginationResult getFeatureNonCompliantDevicesWithDetails(String nonCompliantFeatureCode,
                                                        FilterSet filterSet, int startIndex, int resultCount)
                                                                throws InvalidParameterValueException, SQLException {

        if (nonCompliantFeatureCode == null || nonCompliantFeatureCode.isEmpty()) {
            throw new InvalidParameterValueException("Non-compliant feature code should not be either null or empty.");
        }

        if (startIndex < GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX) {
            throw new InvalidParameterValueException("Start index should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_START_INDEX + " or greater than that.");
        }

        if (resultCount < GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT) {
            throw new InvalidParameterValueException("Result count should be equal to " +
                GadgetDataServiceDAOConstants.Pagination.MIN_RESULT_COUNT + " or greater than that.");
        }

        Map<String, Object> filters = this.extractDatabaseFiltersFromBean(filterSet);

        Connection con;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        List<DetailedDeviceEntry> filteredDevicesWithDetails = new ArrayList<>();
        int totalRecordsCount = 0;
        try {
            con = this.getConnection();
            String sql, advancedSqlFiltering = "";
            // appending filters if exist, to support advanced filtering options
            // [1] appending filter columns, if exist
            if (filters != null && filters.size() > 0) {
                for (String column : filters.keySet()) {
                    advancedSqlFiltering = advancedSqlFiltering + "AND " + column + " = ? ";
                }
            }
            sql = "SELECT * FROM (SELECT ROWNUM offset, rs.* FROM (SELECT DEVICE_ID, DEVICE_IDENTIFICATION, PLATFORM, " +
                "OWNERSHIP, CONNECTIVITY_STATUS FROM " + GadgetDataServiceDAOConstants.DatabaseView.DEVICES_VIEW_2 +
                    " WHERE TENANT_ID = ? AND FEATURE_CODE = ? " + advancedSqlFiltering +
                        "ORDER BY DEVICE_ID ASC) rs) WHERE offset >= ? AND ROWNUM <= ?";
            stmt = con.prepareStatement(sql);
            // [2] appending filter column values, if exist
            stmt.setInt(1, tenantId);
            stmt.setString(2, nonCompliantFeatureCode);
            if (filters != null && filters.values().size() > 0) {
                int i = 3;
                for (Object value : filters.values()) {
                    if (value instanceof Integer) {
                        stmt.setInt(i, (Integer) value);
                    } else if (value instanceof String) {
                        stmt.setString(i, (String) value);
                    }
                    i++;
                }
                stmt.setInt(i, startIndex);
                stmt.setInt(++i, resultCount);
            } else {
                stmt.setInt(3, startIndex);
                stmt.setInt(4, resultCount);
            }
            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            DetailedDeviceEntry filteredDeviceWithDetails;
            while (rs.next()) {
                filteredDeviceWithDetails = new DetailedDeviceEntry();
                filteredDeviceWithDetails.setDeviceId(rs.getInt("DEVICE_ID"));
                filteredDeviceWithDetails.setDeviceIdentification(rs.getString("DEVICE_IDENTIFICATION"));
                filteredDeviceWithDetails.setPlatform(rs.getString("PLATFORM"));
                filteredDeviceWithDetails.setOwnershipType(rs.getString("OWNERSHIP"));
                filteredDeviceWithDetails.setConnectivityStatus(rs.getString("CONNECTIVITY_STATUS"));
                filteredDevicesWithDetails.add(filteredDeviceWithDetails);
            }

            // fetching total records count
            sql = "SELECT COUNT(DEVICE_ID) AS DEVICE_COUNT FROM " + GadgetDataServiceDAOConstants.
                DatabaseView.DEVICES_VIEW_2 + " WHERE TENANT_ID = ? AND FEATURE_CODE = ?";

            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tenantId);
            stmt.setString(2, nonCompliantFeatureCode);

            // executing query
            rs = stmt.executeQuery();
            // fetching query results
            while (rs.next()) {
                totalRecordsCount = rs.getInt("DEVICE_COUNT");
            }
        } finally {
            DeviceManagementDAOUtil.cleanupResources(stmt, rs);
        }
        PaginationResult paginationResult = new PaginationResult();
        paginationResult.setData(filteredDevicesWithDetails);
        paginationResult.setRecordsTotal(totalRecordsCount);
        return paginationResult;
    }

}