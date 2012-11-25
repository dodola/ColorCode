package com.dodola.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

public class DbHelper extends SQLiteOpenHelper {

	private final static String DATABASE_NAME = "color_db";
	private final static int DATABASE_VERSION = 1;
	private final static String TABLE_NAME = "project";
	public final static String FIELD_ID = "_id";
	public final static String FIELD_TITLE = "pro_title";
	public final static String FIELD_DATE = "pro_create_date";
	public final static String FIELD_FOLDER = "pro_folder";
	public final static String FIELD_TYPE = "pro_type";

	public DbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		String sql = "Create table " + TABLE_NAME + "(" + FIELD_ID
				+ " integer primary key autoincrement," + FIELD_TITLE
				+ " text ," + FIELD_DATE + " text ," + FIELD_FOLDER + " text ,"
				+ FIELD_TYPE + " int " + " );";

		db.execSQL(sql);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		String sql = " DROP TABLE IF EXISTS " + TABLE_NAME;
		db.execSQL(sql);
		onCreate(db);
	}

	public Cursor selectAll() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null,
				" _id ");
		return cursor;
	}

	public Cursor selectByType(int type) {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(TABLE_NAME, null, FIELD_TYPE + "=" + type,
				null, null, null, " _id desc");
		return cursor;
	}

	public long insert(String Title, String create_date, String pro_folder,
			int type) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(FIELD_TITLE, Title);
		cv.put(FIELD_DATE, create_date);
		cv.put(FIELD_FOLDER, pro_folder);
		cv.put(FIELD_TYPE, type);
		long row = db.insert(TABLE_NAME, null, cv);
		return row;
	}

	public void delete(int id) {
		SQLiteDatabase db = this.getWritableDatabase();
		String where = FIELD_ID + "=?";
		String[] whereValue = { Integer.toString(id) };
		db.delete(TABLE_NAME, where, whereValue);
	}

	public void update(int id, String Title) {
		SQLiteDatabase db = this.getWritableDatabase();
		String where = FIELD_ID + "=?";
		String[] whereValue = { Integer.toString(id) };
		ContentValues cv = new ContentValues();
		cv.put(FIELD_TITLE, Title);
		db.update(TABLE_NAME, cv, where, whereValue);
	}

}