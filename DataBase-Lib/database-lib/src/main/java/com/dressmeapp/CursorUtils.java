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
import android.database.Cursor;
import android.database.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

public class CursorUtils {

    private static final String TAG = CursorUtils.class.getSimpleName();

    private CursorUtils() {
    }

    /**
     * Convert a cursor in a list of given object
     *
     * @param tableClass type of the object in the list
     * @param cursor     cursor to convert
     * @param <T>        a specialization of {@link com.dressmeapp.TableModel}
     * @return the list corresponding to the given cursor
     */
    public static <T extends TableModel> List<T> cursorToList(Class<T> tableClass, Cursor cursor) {
        return cursorToList(tableClass, cursor, true);
    }

    /**
     * Convert a cursor in a list of given object
     *
     * @param tableClass         type of the object in the list
     * @param cursor             cursor to convert
     * @param automaticallyClose true if you want to automatically close the cursor
     * @param <T>                a specialization of {@link com.dressmeapp.TableModel}
     * @return the list corresponding to the given cursor
     */
    public static <T extends TableModel> List<T> cursorToList(Class<T> tableClass, Cursor cursor, boolean automaticallyClose) {
        try {
            List<T> resultArray = new ArrayList<>();
            ContentValues contentValues = new ContentValues();
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    contentValues.clear();
                    DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
                    try {
                        T A = tableClass.newInstance();
                        A.updateFieldsFrom(contentValues);  //in this way I can exploit the definition of the method available on the specialization of TableModel
                        resultArray.add(A);
                    } catch (TableModel.TableFormatException | InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    cursor.moveToNext();
                }
            }
            return resultArray;
        } finally {
            if (automaticallyClose && cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }
}
