/**
 * Copyright 2015 DressMeApp Development Team (dev.dressmeapp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dressmeapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.dressmeapp.TableModel.Reference;
import com.dressmeapp.TableModel.TableFormatException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import dalvik.system.DexFile;

public abstract class BaseDatabaseHelper {

    private static final String TAG = BaseDatabaseHelper.class.getSimpleName();

    /**
     * @return the name of the SQLite database file
     */
    public abstract String getDatabaseName();

    /**
     * @return version of the SQLite database
     */
    public abstract int getDatabaseVersion();

    private Context context;
    private SQLiteOpenHelper helper;

    public BaseDatabaseHelper(Context context) {
        this.context = context;
        this.helper = new Helper(this);
    }

    /**
     * @return an array of class which extends the TableModel available on the database package and are associated with the current adapter
     * @see <a href="http://stackoverflow.com/questions/15446036/find-all-classes-in-a-package-in-android">Class name from package (Android)</a>
     */
    @SuppressWarnings("unchecked")
    private List<Class<? extends TableModel>> getTables() {
        DexFile df = null;
        List<Class<? extends TableModel>> tableClasses = new ArrayList<>();
        try {
            df = new DexFile(context.getPackageCodePath());
            for (Enumeration<String> enumeration = df.entries(); enumeration.hasMoreElements(); ) {
                try {
                    Class<?> classTmp = Class.forName(enumeration.nextElement());
                    if (!TableModel.class.equals(classTmp) && TableModel.class.isAssignableFrom(classTmp)
                            && classTmp.getAnnotation(TableModel.Table.class).helperClass().equals(getClass())) {
                        tableClasses.add((Class<? extends TableModel>) classTmp);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) {
                    //will be caught only the class that are declared in the package but for which there is any ".class" file
                }
            }
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        } finally {
            if (df != null) {
                try {
                    df.close();
                } catch (IOException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
        }
        return tableClasses;
    }

    /**
     * Order the available database tables according in order to
     * avoid creation of table with foreign keys to table not yet created
     *
     * @return - ordered list of table classes of the database
     */
    public List<Class<? extends TableModel>> getOrderedTables() {
        HashMap<Class<? extends TableModel>, Node> graph = new HashMap<>();
        for (Class<? extends TableModel> tableClass : getTables()) {
            graph.put(tableClass, new Node(tableClass));
        }
        List<Class<? extends TableModel>> ordered = new LinkedList<>();
        try {
            for (Node actual : graph.values()) {
                visit(graph, ordered, actual);
            }
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return ordered;
    }

    /**
     * Recursive function for Depth First Search in the Graph of tables.
     *
     * @param graph
     * @param ordered - ordered table class
     * @param start   - current node
     * @throws Exception if any loop detected
     */
    @SuppressWarnings("unchecked")
    private static void visit(HashMap<Class<? extends TableModel>, Node> graph, List<Class<? extends TableModel>> ordered, Node start) throws Exception {
        switch (start.color) {
            case WHITE:
                start.color = GraphColor.GREY;
                if (start.references != null) {
                    for (Class<? extends TableModel> nextClass : start.references) {
                        final Node next = graph.get(nextClass);
                        if (next.color == GraphColor.GREY) {
                            throw new Exception("References Loop Detected. Check the tables: " +
                                    TableModel.getTableName(start.tableClass) + ", " +
                                    TableModel.getTableName(nextClass));
                        }
                        visit(graph, ordered, next);
                    }
                }
                start.color = GraphColor.BLACK;
                ordered.add(start.tableClass);
            default:
                break;
        }
    }

    /**
     * Colors available for label the node in the graph
     */
    private enum GraphColor {
        WHITE, GREY, BLACK
    }

    /**
     * Describe a Node of the graph needed to allow the correct generation
     * of tables with FOREIGN KEYS
     */
    private static class Node {
        private Class<? extends TableModel> tableClass;
        private List<Class<? extends TableModel>> references;
        private GraphColor color = GraphColor.WHITE;

        public Node(Class<? extends TableModel> tableClass) {
            this.tableClass = tableClass;
            this.references = new ArrayList<>();
            for (Field field : TableModel.getFieldClassFromTableClass(tableClass).getDeclaredFields()) {
                Reference reference = field.getAnnotation(Reference.class);
                if (reference != null) {
                    references.add(reference.referencedTable());
                }
            }
        }
    }


    /**
     * @return the list of the SQL code for the table creation
     * @throws TableModel.TableFormatException if at least one table is not compliant with the table specification
     */
    public ArrayList<String> createTables() throws TableModel.TableFormatException {
        final List<Class<? extends TableModel>> tables = getOrderedTables();
        final ArrayList<String> sqls = new ArrayList<>(tables.size());
        for (Class<? extends TableModel> table : tables) {
            sqls.add(TableModel.createTable(table));
        }
        return sqls;
    }

    /**
     * @return the list of the SQL code for the table deletion
     * @throws TableModel.TableFormatException if at least one table is not compliant with the table specification
     */
    public ArrayList<String> deleteTables() throws TableModel.TableFormatException {
        final List<Class<? extends TableModel>> tables = getOrderedTables();
        final ArrayList<String> sqls = new ArrayList<>(tables.size());
        Collections.reverse(tables);
        for (Class<? extends TableModel> table : tables) {
            sqls.add(TableModel.deleteTable(table));
        }
        return sqls;
    }

    /**
     * @return return the name of the tables managed from the current database helper
     */
    public List<String> getTablesName() {
        List<Class<? extends TableModel>> tables = getTables();
        List<String> tablesNames = new ArrayList<>(tables.size());
        for (Class<? extends TableModel> tableClass : tables) {
            tablesNames.add(TableModel.getTableName(tableClass));
        }
        return tablesNames;
    }

    public void onCreate(SQLiteDatabase db) {
        try {
            for (String sql : createTables()) {
                db.execSQL(sql);
            }
        } catch (TableFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        onEndCreate();
    }

    public void onEndCreate() {
    }

    public void onDelete(SQLiteDatabase db) {
        try {
            for (String sql : deleteTables()) {
                db.execSQL(sql);
            }
        } catch (TableFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        onEndDelete();
    }

    public void onEndDelete() {
    }

    public void onTruncate(SQLiteDatabase db) {
        onDelete(db);
        onCreate(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onTruncate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onTruncate(db);
    }

    public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON;");  //allow to use effectively the foreign keys
    }

    public SQLiteDatabase getWritableDatabase() {
        return helper.getWritableDatabase();
    }

    public SQLiteDatabase getReadableDatabase() {
        return helper.getReadableDatabase();
    }

    public void close() {
        helper.close();
    }

    static class Helper extends SQLiteOpenHelper {
        private BaseDatabaseHelper baseDatabaseHelper;

        public Helper(BaseDatabaseHelper baseDatabaseHelper) {
            super(baseDatabaseHelper.context.getApplicationContext(), baseDatabaseHelper.getDatabaseName(), null, baseDatabaseHelper.getDatabaseVersion());
            this.baseDatabaseHelper = baseDatabaseHelper;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            baseDatabaseHelper.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            baseDatabaseHelper.onUpgrade(db, oldVersion, newVersion);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            baseDatabaseHelper.onDowngrade(db, oldVersion, newVersion);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            baseDatabaseHelper.onConfigure(db);
        }

    }

}
