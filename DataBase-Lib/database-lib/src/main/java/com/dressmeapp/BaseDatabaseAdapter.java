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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;

import java.util.List;

public abstract class BaseDatabaseAdapter {
    private static final String TAG = BaseDatabaseAdapter.class.getSimpleName();
    private static final String COUNT_COLUMN = "cnt";
    private static final String ARRAY_COUNT_COLUMN[] = new String[]{"count(*) as " + COUNT_COLUMN};
    private SQLiteDatabase database;
    private Context context;
    private BaseDatabaseHelper dbHelper;

    public BaseDatabaseAdapter(Context context) {
        this.context = context.getApplicationContext();
    }

    public abstract BaseDatabaseHelper getDatabaseHelper(Context context);

    /**
     * Open a connection with the database that allows data reading and writing
     *
     * @return this BaseDatabaseAdapter
     * @throws SQLException if the database cannot be opened for writing read/write database object valid until {@link #close} is called
     */
    public BaseDatabaseAdapter openWritable() throws SQLException {
        if (dbHelper == null) {
            dbHelper = getDatabaseHelper(context);
        }
        if (database == null || !database.isOpen() || database.isReadOnly()) {
            database = dbHelper.getWritableDatabase();
        }
        return this;
    }

    /**
     * Open a connection with the database that allows data reading only
     *
     * @return this BaseDatabaseAdapter
     * @throws SQLException if the database cannot be opened
     */
    public BaseDatabaseAdapter openReadable() throws SQLException {
        if (dbHelper == null) {
            dbHelper = getDatabaseHelper(context);
        }
        if (database == null || !database.isOpen()) {
            database = dbHelper.getReadableDatabase();
        }
        return this;
    }

    /**
     * Close the connection to the database
     */
    public void close() {
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    /**
     * Delete the content of all the tables in the current database
     */
    public void truncateTables() {
        dbHelper.onTruncate(database);
    }

    /**
     * Insert a tuple in its table and update the value with the information effectively stored into the database.
     *
     * @param value tuple to be added
     * @param <T>   specialization of {@link com.dressmeapp.TableModel}
     * @return {@code true} if the {@code value} is correctly inserted
     */
    public <T extends TableModel> boolean insert(T value) {
        return insert(value, true);
    }

    /**
     * Insert a tuple in its table and update the value with the information effectively stored into the database (if enabled).
     *
     * @param value        tuple to be added
     * @param updateObject true if you want to update the object {@code value} with the data saved into the database (update also the assigned primary key)
     * @param <T>          specialization of {@link com.dressmeapp.TableModel}
     * @return {@code true} if the {@code value} is correctly inserted
     */
    public <T extends TableModel> boolean insert(T value, boolean updateObject) {
        if (value == null) {
            return false;
        }
        try {
            long rowId = database.insertOrThrow(value.getTableName(), null, value.getContentValues());
            if (updateObject && rowId != -1) {
                TableModel result = select(value.getClass(), value.getTableName(), null, "rowId=?", new String[]{"" + rowId}, null, null, null).get(0);
                value.updateFieldsFrom(result);
            }
            return rowId != -1;

        } catch (TableModel.TableFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    /**
     * Update values of the specified tuple
     *
     * @param value tuple to be saved
     * @param <T>   a specialization of {@link com.dressmeapp.TableModel}
     * @return {@code true} if the {@code value} is correctly updated
     */
    public <T extends TableModel> boolean update(T value) {
        try {
            ContentValues contentValues = value.getContentValues();
            return database.update(value.getTableName(), contentValues, value.getPrimaryWhereClause(), null) != 0;
        } catch (TableModel.TableFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    /**
     * Delete the specified tuple from its table
     *
     * @param value tuple to be deleted
     * @param <T>   a specialization of {@link com.dressmeapp.TableModel}
     * @return {@code true} if the {@code value} is correctly deleted
     */
    public <T extends TableModel> boolean delete(T value) {
        try {
            return database.delete(value.getTableName(), value.getPrimaryWhereClause(), null) != 0;
        } catch (TableModel.TableFormatException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    /**
     * Select all tuple in {@code tableClass}'s table
     *
     * @param tableClass model of the data to retrieve
     * @param <T>        a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> selectAll(Class<T> tableClass) {
        return CursorUtils.cursorToList(tableClass, database.query(TableModel.getTableName(tableClass), null, null, null, null, null, null));
    }

    /**
     * Extract the count value stored into the cursor. The cursor is supposed to contain only one row and the column named {@code COUNT_COLUMN}.
     * Automatically close the cursor at the end of the execution.
     *
     * @param cursor cursor to convert
     * @return the value contained from the {@code COUNT_COLUMN} in the cursor, -1 in case of errors
     */
    private static long extractCountValue(Cursor cursor) {
        return extractCountValue(cursor, true);
    }

    /**
     * Extract the count value stored into the cursor. The cursor is supposed to contain only one row and the column named {@code COUNT_COLUMN}.
     *
     * @param cursor             cursor to convert
     * @param automaticallyClose true if you want to automatically close the cursor
     * @return the value contained from the {@code COUNT_COLUMN} in the cursor, -1 in case of errors
     */
    private static long extractCountValue(Cursor cursor, boolean automaticallyClose) {
        try {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                return cursor.getLong(cursor.getColumnIndex(COUNT_COLUMN));
            } else {
                return -1;
            }
        } finally {
            if (automaticallyClose && cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    /**
     * Count all tuple in {@code tableClass}'s table
     *
     * @param tableClass model of the data to retrieve
     * @param <T>        a specialization of {@link com.dressmeapp.TableModel}
     * @return the value contained from the {@code COUNT_COLUMN} in the cursor, -1 in case of errors
     */
    public <T extends TableModel> long countAll(Class<T> tableClass) {
        return extractCountValue(database.query(TableModel.getTableName(tableClass), ARRAY_COUNT_COLUMN, null, null, null, null, null));
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)} method.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param distinct      like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param columns       like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param limit         like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param type          object class to be returned
     * @param <T>           a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> select(Class<T> type, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return CursorUtils.cursorToList(type, database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * Extract the number of tuple reachable from the {@link com.dressmeapp.BaseDatabaseAdapter#select(Class, boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)} method.
     * Is suggested to use this method because it will retrieve only the number of tuple and not the contents.
     * The fields have the same goal as in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param distinct      like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @param limit         like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String)}
     * @return number of filtered tuple reachable from the database, -1 in case of error
     */
    public long count(boolean distinct, String table, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return extractCountValue(database.query(distinct, table, ARRAY_COUNT_COLUMN, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)} method.
     *
     * NOTE: Set null each params when not needed.
     * NOTE: if the API of the device is below JELLY_BEAN the {@code cancellationSignal} will be omitted.
     *
     * @param type               object class to be returned
     * @param distinct           like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param table              like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param columns            like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param selection          like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param selectionArgs      like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param groupBy            like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param having             like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param orderBy            like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param limit              like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param cancellationSignal like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param <T>                a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> select(Class<T> type, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return CursorUtils.cursorToList(type, database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, cancellationSignal));
        } else {
            return select(type, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
        }
    }

    /**
     * Extract the number of tuple reachable from the {@link com.dressmeapp.BaseDatabaseAdapter#select(Class, boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)} method.
     * Is suggested to use this method because it will retrieve only the number of tuple and not the contents.
     * The fields have the same goal as in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}.
     *
     * NOTE: Set null each params when not needed.
     * NOTE: if the API of the device is below JELLY_BEAN the {@code cancellationSignal} will be omitted.
     *
     * @param distinct           like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param table              like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param selection          like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param selectionArgs      like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param groupBy            like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param having             like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param orderBy            like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param limit              like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @param cancellationSignal like in {@link android.database.sqlite.SQLiteDatabase#query(boolean, String, String[], String, String[], String, String, String, String, android.os.CancellationSignal)}
     * @return number of filtered tuple reachable from the database, -1 in case of error
     */
    public long count(boolean distinct, String table, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return extractCountValue(database.query(distinct, table, ARRAY_COUNT_COLUMN, selection, selectionArgs, groupBy, having, orderBy, limit, cancellationSignal));
        } else {
            return count(distinct, table, selection, selectionArgs, groupBy, having, orderBy, limit);
        }
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)} method.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param type          object class to be returned
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param columns       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param <T>           a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> select(Class<T> type, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return CursorUtils.cursorToList(type, database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * Extract the number of tuple reachable from the {@link com.dressmeapp.BaseDatabaseAdapter#select(Class, String, String[], String, String[], String, String, String)} method.
     * Is suggested to use this method because it will retrieve only the number of tuple and not the contents.
     * The fields have the same goal as in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String)}
     * @return number of filtered tuple reachable from the database, -1 in case of error
     */
    public long count(String table, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return extractCountValue(database.query(table, ARRAY_COUNT_COLUMN, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)} method.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param type          object class to be returned
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param columns       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param limit         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param <T>           a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> select(Class<T> type, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return CursorUtils.cursorToList(type, database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * Extract the number of tuple reachable from the {@link com.dressmeapp.BaseDatabaseAdapter#select(Class, String, String[], String, String[], String, String, String, String)} method.
     * Is suggested to use this method because it will retrieve only the number of tuple and not the contents.
     * The fields have the same goal as in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}.
     *
     * NOTE: Set null each params when not needed.
     *
     * @param table         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param selection     like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param groupBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param having        like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param orderBy       like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @param limit         like in {@link android.database.sqlite.SQLiteDatabase#query(String, String[], String, String[], String, String, String, String)}
     * @return number of filtered tuple reachable from the database, -1 in case of error
     */
    public long count(String table, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return extractCountValue(database.query(table, ARRAY_COUNT_COLUMN, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[])} method.
     *
     * @param type          object class to be returned
     * @param sql           like in {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[])}
     * @param selectionArgs like in {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[])}
     * @param <T>           a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> rawQuery(Class<T> type, String sql, String[] selectionArgs) {
        return CursorUtils.cursorToList(type, database.rawQuery(sql, selectionArgs));
    }

    /**
     * Expose {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[], android.os.CancellationSignal)} method.
     *
     * NOTE: if the API of the device is below JELLY_BEAN the {@code cancellationSignal} will be omitted.
     *
     * @param type               object class to be returned
     * @param sql                like in {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[], android.os.CancellationSignal)}
     * @param selectionArgs      like in {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[], android.os.CancellationSignal)}
     * @param cancellationSignal like in {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[], android.os.CancellationSignal)}
     * @param <T>                a specialization of {@link com.dressmeapp.TableModel}
     * @return list of {@code type} object
     */
    public <T extends TableModel> List<T> rawQuery(Class<T> type, String sql, String[] selectionArgs, CancellationSignal cancellationSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return CursorUtils.cursorToList(type, database.rawQuery(sql, selectionArgs, cancellationSignal));
        } else {
            return rawQuery(type, sql, selectionArgs);
        }
    }

    /**
     * Extract the SQLiteDatabase object to perform operations not exposed in this adapter.
     * The returned object should be checked because it can be null if the database was closed or can be opened only for writing operations.
     *
     * @return SQLiteDatabase object
     */
    public SQLiteDatabase getDatabase() {
        return database;
    }
}