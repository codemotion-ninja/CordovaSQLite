package net.orworks.cordovaplugins;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;
import android.database.Cursor;
import android.database.sqlite.*;

/**
 * This class handles connection with a SQLite database on the device. The database can be residing on the internal or external storage.
 *
 * @author Samik
 */
public class CordovaSQLite extends CordovaPlugin
{
    CallbackContext _callbackContext = null;
    SQLiteDatabase myDb = null; // Database object

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArry of arguments for the plugin.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return Returns true to indicate successful axecution (which might have resulted in error),
     * false results in a "MethodNotFound" error.
     */
    public boolean execute (String action, JSONArray args, CallbackContext callbackContext) throws JSONException
    {
        //Log.d("CordovaSQLite", "Plugin called for: " + action);

        _callbackContext = callbackContext;

        if (action.equals("openDatabase"))
        {
            this.openDatabase(args.getString(0), args.getInt(1));
            return true;
        }
        else if (action.equals("execQuerySingleResult"))
        {
            this.execQuerySingleResult(args.getString(0), getStringArray(args.getJSONArray(1)));
            return true;
        }
        else if (action.equals("execQueryArrayResult"))
        {
            this.execQueryArrayResult(args.getString(0), getStringArray(args.getJSONArray(1)));
            return true;
        }
        else if (action.equals("execQueryNoResult"))
        {
            this.execQueryNoResult(getStringArray(args.getJSONArray(0)));
            return true;
        }
        else if (action.equals("closeDB"))
        {
            this.closeDB();
            return true;
        }

        return false;
    }

    /**
     * Open a database.
     *
     * @param fullDBFilePath
     */
    private void openDatabase (String fullDBFilePath, int toCreate)
    {
        // If database is open, then close it
        if (this.myDb != null)
        {
            try
            {
                this.myDb.close();
            }
            catch (SQLiteException ex)
            {
                // Just catch and ignore the exception.
                Log.d("CordovaSQLite", ex.getMessage());
                this.myDb = null;
            }
        }

        // Check if we have got a file URL (i.e., a string starting with file://).
        // In that case, we will discard the file:// part.
        if (fullDBFilePath.startsWith("file://"))
            fullDBFilePath = fullDBFilePath.substring(7);
        Log.d("CordovaSQLite", "Opening database: " + fullDBFilePath);

        try
        {
            if (toCreate == 0)
                myDb = SQLiteDatabase.openDatabase(fullDBFilePath, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            else
                myDb = SQLiteDatabase.openDatabase(fullDBFilePath, null, SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            _callbackContext.success();
        }
        catch (SQLiteException ex)
        {
            Log.d("CordovaSQLite", "Can't open database: " + ex.getMessage());
            _callbackContext.error(ex.getMessage());
        }
    }

    /**
     * Exec query to get a single result value.
     *
     * @param query
     * @param args
     * @return result.
     */
    private void execQuerySingleResult (String query, String[] args)
    {
        //Log.d("CordovaSQLite", "Executing query: " + query + " with arg: " + args[0]);
        try
        {
            String result = null;
            Cursor cursor = myDb.rawQuery(query, args);
            if (cursor.moveToFirst())
                result = cursor.getString(0);
            cursor.close();
            _callbackContext.success(result);
        }
        catch (SQLiteException ex)
        {
            Log.d("CordovaSQLite", ex.getMessage());
            _callbackContext.error(ex.getMessage());
        }
    }

    /**
     * Execute a query and return a 2D JSON array. Rows are records and columns are data cols.
     *
     * @param query
     * @param args
     * @return
     */
    private void execQueryArrayResult (String query, String[] args)
    {
        /*
    	Log.d("CordovaSQLite", "Executing query: " + query + " with arg: ");
    	for (String string : args)
    		Log.d("CordovaSQLite", string);
    	*/

        try
        {
            Cursor cursor = myDb.rawQuery(query, args);

            String resultStr = "[";
            // If query result has rows
            if (cursor.moveToFirst())
            {
                int colCount = cursor.getColumnCount();
                do
                {
                    String val = cursor.getString(0);
                    String rowStr = (val == null ? "[null" : "[\"" + cursor.getString(0) + "\"");
                    for (int i = 1; i < colCount; i++)
                    {
                        val = cursor.getString(i);
                        rowStr += (val == null ? ", null" : ", \"" + cursor.getString(i) + "\"");
                    }
                    rowStr += "]";
                    resultStr += rowStr + ", ";
                    // Keep adding rows till we have around 7000 characters. Beyond that, we
                    // get a 'Syntax Error' when the result is passed to javascript.
                    // Can possibly go beyond 7000, haven't tried. Gives error at around 12000.
                    if (resultStr.length() > 7000)
                        break;
                } while (cursor.moveToNext());

                resultStr = resultStr.substring(0, resultStr.lastIndexOf(","));
            }
            resultStr += "]";
            //Log.d("CordovaSQLite", "Result rowcount=" + cursor.getCount());
            //Log.d("CordovaSQLite", "Result=" + resultStr);
            cursor.close();
            // Set up the result object.
            _callbackContext.success(resultStr);
        }
        catch (SQLiteException ex)
        {
            Log.d("CordovaSQLite", ex.getMessage());
            _callbackContext.error(ex.getMessage());
        }
    }

    /**
     * Execute set of queries which return no value (like insert, update etc.)
     *
     * @param queries A string array containing the queries.
     */
    private void execQueryNoResult (String[] queries)
    {
        try
        {
            for (String query : queries)
            {
                //Log.d("CordovaSQLite", "Executing query: " + query);
                myDb.execSQL(query);
            }
            _callbackContext.success();
        }
        catch (SQLiteException ex)
        {
            Log.d("CordovaSQLite", ex.getMessage());
            _callbackContext.error(ex.getMessage());
        }
    }

    /**
     * Closes a DB safely.
     *
     * @return
     */
    private void closeDB ()
    {
        if (this.myDb != null)
        {
            this.myDb.close();
            this.myDb = null;
        }
        _callbackContext.success();
    }

    /**
     * Convert a JSONArray object to a string array.
     *
     * @param array
     * @return
     * @throws JSONException
     */
    private String[] getStringArray (JSONArray array) throws JSONException
    {
        String[] strArray = new String[array.length()];
        for (int i = 0; i < strArray.length; i++)
            strArray[i] = array.getString(i);
        return strArray;
    }

    /**
     * Clean up and close database.
     */
    @Override
    public void onDestroy ()
    {
        if (this.myDb != null)
        {
            this.myDb.close();
            this.myDb = null;
        }
    }
}