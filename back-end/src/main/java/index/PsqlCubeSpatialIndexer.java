package index;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import main.Config;
import main.DbConnector;
import main.Main;
import project.Canvas;
import project.Layer;
import project.Transform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class PsqlCubeSpatialIndexer extends Indexer {

    private static PsqlCubeSpatialIndexer instance = null;

    private PsqlCubeSpatialIndexer() {}

    public static synchronized PsqlCubeSpatialIndexer getInstance() {
        if (instance == null)
            instance = new PsqlCubeSpatialIndexer();
        return instance;
    }

    @Override
    public void createMV(Canvas c, int layerId) throws Exception {
        Statement bboxStmt = DbConnector.getStmtByDbName(Config.databaseName);
        Connection dbConn = DbConnector.getDbConn(Config.dbServer, Config.databaseName, Config.userName, Config.password);

        Layer l = c.getLayers().get(layerId);
        Transform trans = l.getTransform();
        ArrayList colNames = trans.getColumnNames();

        // step 0: create tables for storing bboxes and tiles
        // put all canvases and layers in same table
        String bboxTableName = "bbox_" + Main.getProject().getName();

        // create extension if it doesn't exist
        String extSql = "create extension if not exists cube;";
        bboxStmt.executeUpdate(extSql);

        // drop table if exists
        String sql = "drop table if exists " + bboxTableName + ";";
        bboxStmt.executeUpdate(sql);

        // create the bbox table
        sql = "create table " + bboxTableName + " (";
        for (int i = 0; i < colNames.size(); i++) {
            sql += colNames.get(i) + " text, ";
        }

        // need to add value based on canvas id and layer id
        sql += "cx double precision, cy double precision, minx double precision, miny double precision, maxx double precision, maxy double precision, v cube;";
        bboxStmt.executeUpdate(sql);

        // if this is an empty layer, return
        if (trans.getDb().equals(""))
            return ;

        // step 1: set up nashorn environment -- what is nashorn??
        NashornScriptEngine engine = null;
        if (! trans.getTransformFunc().equals(""))
            engine = setupNashorn(trans.getTransformFunc());

        // step 2: looping through query results
        // what's difference btwn separable and non-separable cases?
        Statement rawDBStmt = DbConnector.getStmtByDbName(trans.getDb());
        ResultSet rs = DbConnector.getQueryResultIterator(rawDBStmt, trans.getQuery());
        int numColumn = rs.getMetaData().getColumnCount();
        int rowCount = 0;
        String insertSql = "insert into " + bboxTableName + " values (";
        // where does the 6 come from?
        for (int i = 0; i < colNames.size() + 6; i ++) {
            insertSql += "?, ";
        }
        insertSql += "cube(?)";
        PreparedStatement preparedStmt = dbConn.prepareStatement(insertSql);
        while (rs.next()) {

            // count log
            rowCount ++;
            if (rowCount % 1000000 == 0)
                System.out.println(rowCount);

            // get raw row
            ArrayList<String> curRawRow = new ArrayList<>();
            for (int i = 1; i <= numColumn; i ++)
                curRawRow.add(rs.getString(i));

            // step 3: run transform function on this tuple
            ArrayList<String> transformedRow;
            if (! trans.getTransformFunc().equals(""))
                transformedRow = getTransformedRow(c, curRawRow, engine);
            else
                transformedRow = curRawRow;

            // step 4: calculate bounding boxes
            ArrayList<Double> curBbox = getBboxCoordinates(c, l, transformedRow);

            // insert into bbox table
            for (int i = 0; i < transformedRow.size(); i ++)
                preparedStmt.setString(i + 1, transformedRow.get(i).replaceAll("\'", "\'\'"));
            for (int i = 0; i < 6; i ++)
                preparedStmt.setDouble(transformedRow.size() + i + 1, curBbox.get(i));

            double minx, miny, maxx, maxy;
            minx = curBbox.get(2);
            miny = curBbox.get(3);
            maxx = curBbox.get(4);
            maxy = curBbox.get(5);
            // assume that canvas id is an int, not sure if that is part of spec...
            int canvasId = Integer.parseInt(c.getId());
            preparedStmt.setString(transformedRow.size() + 7,
                    // getPolygonText(minx, miny, maxx, maxy));
                    getCubeText(minx, miny, maxx, maxy, canvasId));
            preparedStmt.addBatch();

            if (rowCount % Config.bboxBatchSize == 0) {
                preparedStmt.executeBatch();
                DbConnector.commitConnection(Config.databaseName);
            }
        }
        rs.close();
        rawDBStmt.close();
        DbConnector.closeConnection(trans.getDb());

        // insert tail stuff
        if (rowCount % Config.bboxBatchSize != 0) {
            preparedStmt.executeBatch();
            DbConnector.commitConnection(Config.databaseName);
        }
        preparedStmt.close();

        // create index

        /*
        sql:
            create index idx_tbl_cube_1 on tbl_cube using gist (v);
        */
        sql = "create index 3d_" + bboxTableName + " on " + bboxTableName + " using gist (v);";
        bboxStmt.executeUpdate(sql);
        DbConnector.commitConnection(Config.databaseName);
        bboxStmt.close();
    }

    @Override
    public ArrayList<ArrayList<String>> getDataFromRegion(Canvas c, int layerId, String regionWKT, String predicate)
            throws Exception {
        
        // get column list string
        String colListStr = c.getLayers().get(layerId).getTransform().getColStr("");

        // construct range query
        String sql = "select " + colListStr + " from bbox_" + Main.getProject().getName()
                + " where v && ";
        sql += regionWKT;
        if (predicate.length() > 0) 
            sql += " and " + predicate + ";";
        else
            sql += ";";
        System.out.println(sql);

        // return
        return DbConnector.getQueryResult(Config.databaseName, sql);
    }

    @Override
    public ArrayList<ArrayList<String>> getDataFromTile(Canvas c, int layerId, int minx, int miny, String predicate)
            throws Exception {

        // get column list string
        String colListStr = c.getLayers().get(layerId).getTransform().getColStr("");
        
        // make bounding box cube to intersect with
        String tileCube = "cube (" + 
            "array [" + minx + ", " + miny + ", " + c.getId() + "], " +
            "array [" + minx + ", " + (miny + Config.tileH) + ", " + c.getId() + "], " +
            "array [" + (minx + Config.tileW) + ", " + (miny + Config.tileH) + ", " + c.getId() + "] )";
        
        // construct range query
        String sql = "select " + colListStr + " from bbox_" + Main.getProject().getName()
                + " where v && " + tileCube;
        
        if (predicate.length() > 0)
            sql += " and " + predicate + ";";
        else
            sql += ";";
        System.out.println(minx + " " + miny + " " + c.getId() + " : " + sql);

        // return
        return DbConnector.getQueryResult(Config.databaseName, sql);
    }

}