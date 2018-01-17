/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
package org.wso2.carbon.device.application.mgt.core.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.device.application.mgt.common.Comment;
import org.wso2.carbon.device.application.mgt.common.PaginationRequest;
import org.wso2.carbon.device.application.mgt.common.PaginationResult;
import org.wso2.carbon.device.application.mgt.common.exception.ApplicationManagementException;
import org.wso2.carbon.device.application.mgt.common.exception.CommentManagementException;
import org.wso2.carbon.device.application.mgt.common.exception.DBConnectionException;
import org.wso2.carbon.device.application.mgt.common.exception.TransactionManagementException;
import org.wso2.carbon.device.application.mgt.common.services.*;
import org.wso2.carbon.device.application.mgt.core.dao.CommentDAO;
import org.wso2.carbon.device.application.mgt.core.dao.common.ApplicationManagementDAOFactory;
import org.wso2.carbon.device.application.mgt.core.dao.common.Util;
import org.wso2.carbon.device.application.mgt.core.exception.ApplicationManagementDAOException;
import org.wso2.carbon.device.application.mgt.core.util.ConnectionManagerUtil;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * This class is the default implementation for the Managing the comments.
 */
@SuppressWarnings( "deprecation" )
public class CommentsManagerImpl implements CommentsManager {

    private static final Log log = LogFactory.getLog(CommentsManagerImpl.class);
    private CommentDAO commentDAO;

    public CommentsManagerImpl() {
        initDataAccessObjects();
    }

    private void initDataAccessObjects() {
        this.commentDAO= ApplicationManagementDAOFactory.getCommentDAO();
    }

    @Override
    public Comment addComment(Comment comment,String uuid,int tenantId) throws CommentManagementException {

        if (log.isDebugEnabled()) {
            log.debug("Request for comment is received for uuid" +uuid);
        }
        comment.setCreatedAt(Timestamp.from(Instant.now()));

        try {
            ConnectionManagerUtil.beginDBTransaction();
            commentDAO.addComment(tenantId,comment, comment.getCreatedBy(),comment.getParent(),uuid);
            ConnectionManagerUtil.commitDBTransaction();
            return comment;
        } catch (Exception e) {
            log.error("Exception occurs.", e);
            ConnectionManagerUtil.rollbackDBTransaction();
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return comment;
    }

    /**
     * To validate the pre-request of the comment
     *
     * @param CommentId ID of the comment.
     * @param comment comment needed to be validate.
     * @return Application related with the UUID.
     *
     *
     */

    public Boolean validateComment(int CommentId,String comment) throws CommentManagementException{

        if (CommentId <= 0) {
            throw new CommentManagementException("Comment ID is null or negative. Comment id is a required parameter to get the " +
                    "relevant comment.");
        }
        if(comment==null){
                log.error("Null Point Exception.Comment at comment id "+CommentId+" is null.");
                return false;
        }
        return true;
    }


    @Override
    public List<Comment> getAllComments(PaginationRequest request,String uuid) throws CommentManagementException,
            SQLException {

        PaginationResult paginationResult = new PaginationResult();
        List<Comment> comments;
        request = Util.validateCommentListPageSize(request);


        if (log.isDebugEnabled()) {
            log.debug("get all comments of the application release"+uuid);
        }

        try {
            ConnectionManagerUtil.openDBConnection();
            comments=commentDAO.getAllComments(uuid,request);
            int count=commentDAO.getCommentCount(request,uuid);
            paginationResult.setData(comments);
            paginationResult.setRecordsFiltered(count);
            paginationResult.setRecordsTotal(count);

            return comments;
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return null;
    }

    @Override
    public Comment getComment(int CommentId) throws CommentManagementException {

        PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId(true);
        Comment comment=null;

        if (log.isDebugEnabled()) {
            log.debug("Comment retrieval request is received for the comment id " + CommentId);
        }

        try {
            ConnectionManagerUtil.openDBConnection();
            comment=commentDAO.getComment(CommentId);
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } catch (SQLException e) {
            log.error("SQL Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return comment;
    }

    @Override
    public void deleteComment(int CommentId) throws CommentManagementException {

        Comment comment;
        comment= getComment(CommentId);

        if (comment == null) {
            throw new CommentManagementException(
                        "Cannot delete a non-existing comment for the application with comment id" + CommentId);
        }
        try {
            ConnectionManagerUtil.beginDBTransaction();
            commentDAO.deleteComment(CommentId);
            ConnectionManagerUtil.commitDBTransaction();
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } catch (SQLException e) {
            log.error("SQL Exception occurs.", e);
        } catch (TransactionManagementException e) {
            log.error("Transaction Management Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
    }

    @Override
    public Comment updateComment(Comment comment,int CommentId) throws CommentManagementException, SQLException,
            DBConnectionException {

        PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId(true);
        validateComment(CommentId,comment.getComment());

        if (log.isDebugEnabled()) {
            log.debug("Comment retrieval request is received for the comment id " + CommentId);
        }
        comment.setModifiedAt(Timestamp.from(Instant.now()));
        try {
            ConnectionManagerUtil.openDBConnection();
            commentDAO.getComment(CommentId);
            return commentDAO.updateComment(CommentId,
                    comment.getComment(),comment.getModifiedBy(),comment.getModifiedAt());
        } catch (SQLException e) {
            log.error("SQL Exception occurs.", e);
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return commentDAO.getComment(CommentId);
    }

    public int getRatedUser(String uuid){

        int ratedUsers=0;
        if (log.isDebugEnabled()) {
            log.debug("Get the rated users for the application release number"+uuid);
        }
        try {
            ConnectionManagerUtil.openDBConnection();
            ratedUsers =commentDAO.getRatedUser(uuid);
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } catch (ApplicationManagementDAOException e) {
            log.error("Application Management Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return ratedUsers;
    }

    @Override
    public int getStars(String uuid) throws SQLException {

        int stars=0;
        if (log.isDebugEnabled()) {
            log.debug("Get the average of rated stars for the application "+uuid);
        }
        try {
            ConnectionManagerUtil.openDBConnection();
            stars= commentDAO.getStars(uuid);
        } catch (DBConnectionException e) {
            log.error("DB Connection Exception occurs.", e);
        } catch (ApplicationManagementDAOException e) {
            log.error("Application Management Exception occurs.", e);
        } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return stars;
    }

    @Override
    public int updateStars(int stars , String uuid) throws ApplicationManagementException {

        if (log.isDebugEnabled()) {
            log.debug("Stars are received for the application " + uuid);
        }
        int newStars=0;
        try {
            ConnectionManagerUtil.beginDBTransaction();

            int ratedUsers = commentDAO.getRatedUser(uuid);
            int oldStars = commentDAO.getStars(uuid);
            if(ratedUsers==0){
                newStars=commentDAO.updateStars(stars,uuid);
                return newStars;
            }else {
                int avgStars = ((oldStars*ratedUsers)+stars) / (ratedUsers+1);
                newStars = commentDAO.updateStars(avgStars, uuid);
                ConnectionManagerUtil.commitDBTransaction();
                return newStars;
            }
        } catch (ApplicationManagementDAOException e) {
            ConnectionManagerUtil.rollbackDBTransaction();
            log.error("Application Management Exception occurs.", e);

        } catch (DBConnectionException e) {
                log.error("DB Connection Exception occurs.", e);
            } finally {
            ConnectionManagerUtil.closeDBConnection();
        }
        return newStars;
    }


}