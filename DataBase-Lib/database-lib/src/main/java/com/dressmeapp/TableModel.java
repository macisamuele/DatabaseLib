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
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

/**
 * General Model for a SQL Table (SQLite dialect) For performance issue is
 * strongly suggested to Override getContentValues and getObject with a table
 * dependent implementation in order to reduce as much as possible the
 * reflections calls WARNING: in order to avoid execution errors (Exceptions) we
 * recommend to define an empty constructor (one without parameters) inside each
 * table class (in particular if you use the table independent implementations
 * of the functions)
 *
 * REMARK: Reference n to n MUST be managed manually. The easiest way to do that
 * is to define a bridge table that refers to both.
 */
public class TableModel {

    private static final String TAG = TableModel.class.getSimpleName();

    // Name of the "public static class" required inside each class that represents tables
    private static final String FIELD_CLASS_NAME = "Fields";

    /**
     * Retrieve the table name into the database associated with {@code tableClass}
     *
     * @param tableClass class of the table for which you want to know the name of the table
     * @return the assigned table name
     */
    public static String getTableName(Class<? extends TableModel> tableClass) {
        return tableClass.getSimpleName();
    }

    /**
     * Retrieve the database table name associated with this class
     *
     * @return the assigned table name
     */
    public String getTableName() {
        return TableModel.getTableName(this.getClass());
    }

    /**
     * Extract the setter method for the {@code field}
     *
     * @param tableClass     Class of the table for which you want extract the method
     * @param field          field of the database for which you want the setter
     * @param fieldClassType type of the field
     * @return the setter method
     * @throws TableModel.TableFormatException if the tableClass doesn't respect all the constraints to be a database table
     */
    private static Method getSetter(Class<? extends TableModel> tableClass, Field field, Class<?> fieldClassType) throws TableFormatException {
        try {
            Method setter = tableClass.getMethod("set" + StringManipulation.toPascalCase(field.getName()), fieldClassType);
            if (!Modifier.isPublic(setter.getModifiers()) || !(
                    setter.getReturnType().equals(void.class) || setter.getReturnType().equals(Void.class) ||
                            TableModel.class.isAssignableFrom(setter.getReturnType()))) {
                throw TableFormatException.noSetterMethod(tableClass, field.getName(), fieldClassType);
            }
            return setter;
        } catch (SecurityException | NoSuchMethodException e) {
            throw TableFormatException.noSetterMethod(tableClass, field.getName(), fieldClassType);
        }
    }

    /**
     * Extract the getter method for the {@code field}
     *
     * @param tableClass     Class of the table for which you want extract the method
     * @param field          field of the database for which you want the getter
     * @param fieldClassType type of the field
     * @return getter method
     * @throws TableModel.TableFormatException if the tableClass doesn't respect all the constraints to be a database table
     */
    private static Method getGetter(Class<? extends TableModel> tableClass, Field field, Class<?> fieldClassType) throws TableFormatException {
        try {
            Method getter = tableClass.getMethod("get" + StringManipulation.toPascalCase(field.getName()));
            if (!Modifier.isPublic(getter.getModifiers()) || !getter.getReturnType().equals(fieldClassType)) {
                throw TableFormatException.noGetterMethod(tableClass, field.getName(), fieldClassType);
            }
            return getter;
        } catch (SecurityException | NoSuchMethodException e) {
            throw TableFormatException.noGetterMethod(tableClass, field.getName(), fieldClassType);
        }
    }

    /**
     * Verify that the table respect all the condition to be a good database table.
     * Verifies the consistency of the annotations, the existence of the setter and getter methods for
     * all the fields in the database and check (if references exists) the reference consistency
     * (a column can reference to a column of the same type).
     *
     * @param tableClass class of the table for which you want check the constraints
     * @param <T>        specialization of the {code TableModel} class.
     * @throws TableModel.TableFormatException if the tableClass doesn't respect all the constraints to be a database table
     */
    @SuppressWarnings("unchecked")
    public static <T extends TableModel> void checkConstraints(Class<T> tableClass) {
        Column columnAnnotation;
        Reference referenceAnnotation;

        try {
            tableClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) { // handled in the following if
            throw TableFormatException.noEmptyConstructor(tableClass);
        }

        // check the existence of the "public static class Field"
        Class<?> fieldClass = getFieldClassFromTableClass(tableClass);
        boolean good = (fieldClass != null);
        if (!good) {
            throw TableFormatException.noPublicStaticClassField(tableClass);
        }

        Map<String, Field> fieldNames = new TreeMap<>();

        for (Field field : fieldClass.getFields()) {  //todo: check if the fields are accessible
            if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                Class<?> fieldClassType = columnAnnotation.type().getClassType();
                // check if there are the setter method with the right type
                // associated to the column type
                getSetter(tableClass, field, fieldClassType);
                // check if there are the getter method with the right type
                // associated to the column type
                getGetter(tableClass, field, fieldClassType);
                // check name unicity
                String fieldValue = null;
                try {
                    fieldValue = (String) field.get(null);
                } catch (IllegalAccessException e) {
                    Log.e("TableModel", Log.getStackTraceString(e));
                    e.printStackTrace();
                }

                if (fieldNames.containsKey(fieldValue)) {
                    throw TableFormatException.fieldUnicityConstraintViolated(tableClass, fieldValue, field, fieldNames.get(fieldValue));
                } else {
                    fieldNames.put(fieldValue, field);
                }
                // check if there is type matching in case of references
                if ((referenceAnnotation = field.getAnnotation(Reference.class)) != null) {
                    for (Class<?> referencedTableClass : referenceAnnotation.referencedTable().getClasses()) {
                        if (referencedTableClass.getSimpleName().equals(FIELD_CLASS_NAME)) {
                            try {
                                Field referencedField = referencedTableClass.getField(referenceAnnotation.referencedField());
                                if ((referencedField.getAnnotation(Column.class)).type() != columnAnnotation.type()) {
                                    throw TableFormatException.noReferenceTypeMatching(tableClass, field.getName(), (Class<? extends TableModel>) referencedTableClass, referencedField.getName());
                                }
                            } catch (SecurityException | NoSuchFieldException e) {
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param tableClass class of the table for which you want the SQL code
     * @return SQL code for the creation of the table (SQLite dialect)
     * @throws TableFormatException if the tableClass doesn't respect all the constraints to be a database table
     */
    public static String createTable(Class<? extends TableModel> tableClass) throws TableFormatException {
        checkConstraints(tableClass);
        StringBuilder builder = new StringBuilder();
        StringBuilder primaryBuilder = new StringBuilder();
        StringBuilder constraintBuilder = new StringBuilder();
        Column columnAnnotation;
        Reference referenceAnnotation;
        builder.append("CREATE TABLE ").append(getTableName(tableClass)).append(" (");
        Class<?> fieldClass = getFieldClassFromTableClass(tableClass);
        for (Field field : fieldClass.getFields()) {
            if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                try {
                    String fieldName = (String) field.get(null);
                    if (columnAnnotation.primary()) {
                        primaryBuilder.append(fieldName).append(",");
                    }
                    builder.append(fieldName)
                            .append(" ").append(columnAnnotation.type())
                            .append(columnAnnotation.nullable() ? "" : " NOT NULL")
                            .append(columnAnnotation.unique() ? " UNIQUE" : "").append(",");
                    //TODO: any refer to autoincrement /*.append(columnAnnotation.autoincrement() ? " AUTOINCREMENT" : "")*/
                    if ((referenceAnnotation = field.getAnnotation(Reference.class)) != null) {
                        constraintBuilder.append(",")
                                .append("FOREIGN KEY (").append(fieldName).append(") REFERENCES ")
                                .append(getTableName(referenceAnnotation.referencedTable())).append(" (")
                                .append(referenceAnnotation.referencedField()).append(")")
                                .append(" ON DELETE ").append(referenceAnnotation.onDelete().getAction())
                                .append(" ON UPDATE ").append(referenceAnnotation.onUpdate().getAction());
                    }
                } catch (IllegalAccessException e) {  //never happen because already tested the coherency of the tableClass
                }
            }
        }

        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        builder.append(constraintBuilder);

        if (primaryBuilder.length() > 0) {
            primaryBuilder.setLength(primaryBuilder.length() - 1);
            builder.append(",PRIMARY KEY (").append(primaryBuilder).append(")");
        }
        builder.append(");");
        return builder.toString();
    }

    /**
     * @param tableClass class of the table for which you want the SQL code
     * @return SQL code for the elimination of the table (SQLite dialect)
     */
    public static String deleteTable(Class<? extends TableModel> tableClass) {
        return "DROP TABLE IF EXISTS " + getTableName(tableClass) + ";";
    }

    /**
     * @param tableClass class of the table for which you want the Values
     * @param obj        object that contains the information to put in the Values
     * @return ContentValues which contains the information of {@code obj} presented with the {@code tableClass} schema
     */
    public static ContentValues getContentValues(Class<? extends TableModel> tableClass, Object obj) {
        ContentValues contentValues = new ContentValues();
        Column columnAnnotation = null;
        Class<?> fieldClass = getFieldClassFromTableClass(tableClass);
        try {
            for (Field field : fieldClass.getFields()) {
                if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                    String fieldName;
                    fieldName = (String) field.get(null);
                    Method getter = getGetter(tableClass, field, columnAnnotation.type().getClassType());
                    if (getter.invoke(obj) == null) {
                        if (columnAnnotation.nullable()) {
                            contentValues.putNull(fieldName);
                        }
                    } else {
                        switch (columnAnnotation.type()) {
                            case INTEGER:
                                contentValues.put(fieldName, Long.parseLong(getter.invoke(obj).toString()));
                                break;
                            case REAL:
                                contentValues.put(fieldName, Double.parseDouble(getter.invoke(obj).toString()));
                                break;
                            case TEXT:
                                contentValues.put(fieldName, getter.invoke(obj).toString());
                                break;
                            case BLOB:
                                contentValues.put(fieldName, Serializer.serialize(getter.invoke(obj)));
                                break;
                        }
                    }
                }
            }
            return contentValues;
        } catch (IllegalAccessException | InvocationTargetException | ObjectNotSerializableException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return ContentValues which contains the information of obj presented with the tableClass schema
     */
    public ContentValues getContentValues() {
        return TableModel.getContentValues(this.getClass(), this);
    }

    /**
     * @param tableClass    class of the table for which you want the Object
     * @param contentValues ContentValues which contains the information of obj presented with the tableClass schema
     * @param <T>           database table (specialization of {@link TableModel})
     * @return object that contains the information of {@code contentValues}, null if there is no default constructor
     */
    public static <T extends TableModel> T getObject(Class<T> tableClass, ContentValues contentValues) throws TableFormatException {
        Column columnAnnotation;
        Class<?> fieldClass;
        T result;
        try {
            result = tableClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) { // handled in the following if
            Log.e(TAG, "Default constructor (with no parameters) is missing for " + tableClass.getName());
            return null;
        }
        fieldClass = getFieldClassFromTableClass(tableClass);
        try {
            for (Field field : fieldClass.getFields()) {
                if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                    String fieldName;
                    fieldName = (String) field.get(null);
                    Method setter = getSetter(tableClass, field, columnAnnotation.type().getClassType());
                    switch (columnAnnotation.type()) {
                        case INTEGER:
                            setter.invoke(result, contentValues.getAsLong(fieldName));
                            break;
                        case REAL:
                            setter.invoke(result, contentValues.getAsDouble(fieldName));
                            break;
                        case TEXT:
                            setter.invoke(result, contentValues.getAsString(fieldName));
                            break;
                        case BLOB:
                            setter.invoke(result, Serializer.deserialize(contentValues.getAsByteArray(fieldName)));
                            break;
                    }
                }
            }
        } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
        }
        return result;
    }

    /**
     * Extract the class containing the database fields
     *
     * @param tableClass table model representation
     * @return the Fields class
     */
    static Class<?> getFieldClassFromTableClass(Class<? extends TableModel> tableClass) {
        for (Class<?> tmpClass : tableClass.getClasses()) {
            if (tmpClass.getSimpleName().equals(FIELD_CLASS_NAME)) {
                return Modifier.isStatic(tmpClass.getModifiers()) && Modifier.isPublic(tmpClass.getModifiers()) ? tmpClass : null;
            }
        }
        return null;
    }

    /**
     * Build a string to match the current object and return it
     *
     * @return string to be used in query WHERE clause
     * @throws TableFormatException if the tableClass doesn't respect all the constraints to be a database table
     */
    public String getPrimaryWhereClause() {

        Class<? extends TableModel> tableClass = this.getClass();
        checkConstraints(tableClass);
        Class<?> fieldClass = getFieldClassFromTableClass(tableClass);
        StringBuilder primaryBuilder = new StringBuilder();
        Column columnAnnotation;

        try {
            for (Field field : fieldClass.getFields()) {
                if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                    String fieldName = (String) field.get(null);
                    if (columnAnnotation.primary()) {
                        if (primaryBuilder.length() > 0) {
                            primaryBuilder.append(" AND ");
                        }
                        primaryBuilder.append(fieldName).append("=\"")
                                .append(getGetter(tableClass, field, columnAnnotation.type().getClassType()).invoke(this).toString())
                                .append("\"");
                    }
                }
            }
            return primaryBuilder.toString();
        } catch (IllegalAccessException | InvocationTargetException e) {  //should never happens
            throw new RuntimeException(e);
        }
    }

    /**
     * Update the object passed as parameter into the database.
     *
     * @param newValue new value to assign at the database tuple with the same primary key
     * @param <T>      specialization of the {code TableModel} class.
     */
    public <T extends TableModel> void updateFieldsFrom(T newValue) {

        Class<? extends TableModel> tableClass = this.getClass();

        try {
            checkConstraints(tableClass);

            Class<?> fieldClass = getFieldClassFromTableClass(tableClass);

            for (Field field : fieldClass.getFields()) {
                Column columnAnnotation;
                if ((columnAnnotation = field.getAnnotation(Column.class)) != null) {
                    Method getter = getGetter(tableClass, field, columnAnnotation.type().getClassType());
                    Method setter = getSetter(tableClass, field, columnAnnotation.type().getClassType());
                    setter.invoke(this, getter.invoke(newValue));
                }
            }

        } catch (TableFormatException | IllegalAccessException | InvocationTargetException e) {
            Log.e("TableModel", Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    public void updateFieldsFrom(ContentValues contentValues) {
        updateFieldsFrom(getObject(this.getClass(), contentValues));
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Table {
        Class<? extends BaseDatabaseHelper> helperClass();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Column {
        /**
         * @return true if the column is a PRIMARY KEY for the table
         */
        boolean primary() default false;

        /**
         * @return true if the column can be NULL
         */
        boolean nullable() default false;

        /**
         * @return true if the column must be unique
         */
        boolean unique() default false;

//        /**
//         * @return true if the column is autoincrement
//         */
        // DEFAULT WHEN PRIMARY
        // boolean autoincrement() default false;

        /**
         * @return the content type of the column
         */
        SQLiteType type() default SQLiteType.TEXT;

        /**
         * Definition of the types available on the SQLite dialect
         */
        public static enum SQLiteType {
            NULL, INTEGER, REAL, TEXT, BLOB;

            public Class<?> getClassType() {
                switch (this) {
                    case INTEGER:
                        return Long.class;
                    case REAL:
                        return Double.class;
                    case TEXT:
                        return String.class;
                    case BLOB:
                        return Object.class;
                    default:
                        return null;
                }
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Reference {
        Class<? extends TableModel> referencedTable();

        String referencedField();

        SQLiteAction onDelete() default SQLiteAction.CASCADE;

        SQLiteAction onUpdate() default SQLiteAction.CASCADE;

        public static enum SQLiteAction {
            NO_ACTION, RESTRICT, SET_NULL, SET_DEFAULT, CASCADE;

            public String getAction() {
                switch (this) {
                    case NO_ACTION:
                        return "NO ACTION";
                    case RESTRICT:
                        return "RESTRICT";
                    case SET_NULL:
                        return "SET NULL";
                    case SET_DEFAULT:
                        return "SET DEFAULT";
                    case CASCADE:
                        return "CASCADE";
                }
                return "";
            }
        }
    }

    private static class StringManipulation {
        /**
         * Evaluate the Pascal case for a given string, Pascal case is a camel case with the first letter in upper case
         *
         * @param string to translate
         * @return the string in Pascal case
         */
        public static String toPascalCase(String string) {
            StringBuilder builder = new StringBuilder();
            for (String part : string.split("_")) {
                builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
            }
            return builder.toString();
        }
    }

    /**
     * Set of exception related to the wrong definition of a class that represents a database table
     */
    public static class TableFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private TableFormatException(String string) {
            super(string);
        }

        private static String header(Class<? extends TableModel> tableClass) {
            return "\nThe model of the table " + getTableName(tableClass) + " defined in " + tableClass.getCanonicalName() + "\n";
        }

        public static TableFormatException noEmptyConstructor(Class<? extends TableModel> tableClass) {
            return new TableFormatException(header(tableClass) + "doesn't have the empty constructor");
        }

        public static TableFormatException noPublicStaticClassField(Class<? extends TableModel> tableClass) {
            return new TableFormatException(header(tableClass) + "doesn't have the public static class " + TableModel.FIELD_CLASS_NAME);
        }

        public static TableFormatException noSetterMethod(Class<? extends TableModel> tableClass, String fieldName, Class<?> classToSet) {
            return new TableFormatException(header(tableClass) + "doesn't have the setter method for the column " + fieldName + " defined as: " + "public RET_TYPE set"
                    + StringManipulation.toPascalCase(fieldName) + "(" + classToSet.getCanonicalName() + ") where RET_TYPE can be either void, Void or itself");
        }

        public static TableFormatException noGetterMethod(Class<? extends TableModel> tableClass, String fieldName, Class<?> classToGet) {
            return new TableFormatException(header(tableClass) + "doesn't have the getter method for the column " + fieldName + " defined as: " + "public " + classToGet.getCanonicalName() + " get"
                    + StringManipulation.toPascalCase(fieldName) + "()");
        }

        public static TableFormatException noReferenceTypeMatching(Class<? extends TableModel> tableClass, String fieldName, Class<? extends TableModel> referencedTableClass,
                                                                   String referencedFieldName) {
            return new TableFormatException(header(tableClass) + "has reference inconsistency.\n" + TableModel.getTableName(tableClass) + "." + fieldName + " references to "
                    + TableModel.getTableName(referencedTableClass) + "." + referencedFieldName + " but they have different field types.");
        }

        public static TableFormatException fieldUnicityConstraintViolated(Class<? extends TableModel> tableClass, String fieldValue, Field field1, Field field2) {
            return new TableFormatException(header(tableClass) + "has the same value (" + fieldValue.toUpperCase() + ") in fields " + field1.getName() + " and " + field2.getName());
        }
    }

    /**
     * Exception for the Serialization of a not serializable object
     */
    public static class ObjectNotSerializableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ObjectNotSerializableException(String string) {
            super(string);
        }
    }

    /**
     * Allows the conversion of a generic object to bytes and viceversa
     */
    public static class Serializer {
        /**
         * @param obj object to serialize
         * @return array of bytes that contains the exact bytes configuration of the object
         * @throws ObjectNotSerializableException if the object is not serializable
         */
        public static byte[] serialize(Object obj) throws ObjectNotSerializableException {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                ObjectOutputStream o = new ObjectOutputStream(b);
                o.writeObject(obj);
                return b.toByteArray();
            } catch (IOException e) {
                throw new ObjectNotSerializableException("The object MUST BE serializable");
            }
        }

        /**
         * @param bytes array of bytes that contains the exact bytes configuration of the object
         * @return the object contained in the array of bytes
         */
        public static Object deserialize(byte[] bytes) {
            ByteArrayInputStream b = null;
            ObjectInputStream o = null;
            try {
                if (bytes == null) {
                    return null;
                }
                b = new ByteArrayInputStream(bytes);
                o = new ObjectInputStream(b);
                return o.readObject();
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    if (b != null) {
                        b.close();
                    }
                    if (o != null) {
                        o.close();
                    }
                } catch (IOException e) {
                }
            }
        }
    }
}
