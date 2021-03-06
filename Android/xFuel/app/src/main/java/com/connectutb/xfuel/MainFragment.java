package com.connectutb.xfuel;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.connectutb.xfuel.providers.AircraftContract;
import com.connectutb.xfuel.tools.AircraftManager;
import com.connectutb.xfuel.tools.FuelPlanGenerator;

import java.util.HashMap;

public class MainFragment extends Fragment implements AircraftContract{

    // Views
    private Spinner aircraftSpinner;
    private RadioButton radioMetric;
    private RadioButton radioImperial;
    private RadioGroup radioGroupUnits;
    private FloatingActionButton sendFAB;
    private EditText etDeparture;
    private EditText etArrival;
    private Button submitButton;

    private int mStackLevel = 0;
    private HashMap advOptions;
    private SimpleCursorAdapter adapter;


    private SharedPreferences settings;
    private SharedPreferences.Editor editor;


    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static MainFragment newInstance(int sectionNumber) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public MainFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        editor = settings.edit();

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        configureViews(rootView);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mAdvancedOptionsReceiver, new IntentFilter("advancedOptions"));

        populateAircraftSpinner();
        return rootView;
    }


    private BroadcastReceiver mAdvancedOptionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            advOptions = (HashMap<String, String>)intent.getSerializableExtra("options");
        }
    };

    private void configureViews(View rootView){

        // Initialization of Views
        aircraftSpinner = (Spinner) rootView.findViewById(R.id.spinnerAircraft);
        radioMetric = (RadioButton) rootView.findViewById(R.id.radioButtonMetric);
        radioImperial = (RadioButton) rootView.findViewById(R.id.radioButtonImperial);
        radioGroupUnits = (RadioGroup) rootView.findViewById(R.id.radioGroupUnits);
        sendFAB = (FloatingActionButton)  rootView.findViewById(R.id.submitFAB);
        etArrival = (EditText) rootView.findViewById(R.id.editTextArrival);
        etDeparture = (EditText) rootView.findViewById(R.id.editTextDeparture);
        submitButton = (Button) rootView.findViewById(R.id.buttonCalculate);

        // Auto-fill if set to do so in settings
        if (settings.getBoolean("pref_autofill", true)) {
            etArrival.setText(settings.getString("arr_icao", ""));
            etDeparture.setText(settings.getString("dep_icao", ""));

            if (settings.getBoolean("want_metric", true)) {
                radioMetric.setChecked(true);
                radioImperial.setChecked(false);
            } else {
                radioMetric.setChecked(false);
                radioImperial.setChecked(true);
            }
        }

        radioMetric.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked) {
                    editor.putBoolean("want_metric", true).commit();
                } else {
                    editor.putBoolean("want_metric", false).commit();
                }
            }
        });

        submitButton.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View view) {
                showAdvancedDialog();
            }
        });

        sendFAB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                submitFuelParameters();
            }
        });
    }

    private void submitFuelParameters(){
        String depICAO = etDeparture.getText().toString();
        String arrICAO = etArrival.getText().toString();
        if (depICAO.length() == 4 && arrICAO.length() == 4) {
            animateFab();
            FuelPlanGenerator fpg = new FuelPlanGenerator(getActivity());
            boolean wantMetric = settings.getBoolean("want_metric", true);
            // Store last-used values
            editor.putString("dep_icao", depICAO);
            editor.putString("arr_icao", arrICAO);
            editor.putString("aircraft", adapter.getCursor().getString(1));
            editor.putInt("def_aircraft", aircraftSpinner.getSelectedItemPosition());
            editor.commit();

            if (advOptions == null){
                advOptions = new HashMap();
            }
            String aircraftCode = adapter.getCursor().getString(1);
            fpg.generateFuelPlan(depICAO, arrICAO, aircraftCode, advOptions, wantMetric);
        } else {
            Toast.makeText(getActivity(), getString(R.string.error_invalid_airport), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    private void populateAircraftSpinner(){

        AircraftManager am = new AircraftManager(getActivity());
        am.updateAircraftList();

        adapter = new SimpleCursorAdapter(getActivity(), R.layout.aircraft_spinner_row_layout, null,
                new String[] { AIRCRAFT_NAME },
                new int[] { R.id.textViewSpinnerRowAircraftName }, SimpleCursorAdapter.NO_SELECTION);

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    return false;
            }
        });

        //Associate adapter with listView
        aircraftSpinner.setAdapter(adapter);

        LoaderManager loaderManager = getLoaderManager();

        /*
		 * Callback (third argument) is important.
		 *
		 */
        loaderManager.initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {

            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return new CursorLoader(getActivity(), QUERY_AIRCRAFT_ITEM, null, null, null, null);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                //Loaded our data.
                ((SimpleCursorAdapter) aircraftSpinner.getAdapter()).swapCursor(data);
                //Set previously used aircraft if preferred.
                if (settings.getBoolean("pref_autofill", true)) {
                    aircraftSpinner.setSelection(settings.getInt("def_aircraft", 0));
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> arg0) {
                //We got nothing there, so swap it back to null
                ((SimpleCursorAdapter) aircraftSpinner.getAdapter()).swapCursor(null);
            }

        });
        }

    private void animateFab(){
        AnimationSet animationSet = new AnimationSet(true);

        RotateAnimation r =  new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        r.setDuration(400);
        r.setRepeatCount(1);
        //r.setRepeatMode(Animation.);
        animationSet.addAnimation(r);
        sendFAB.startAnimation(animationSet);

    }

    private void showAdvancedDialog() {
        mStackLevel++;

        // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = AdvancedOptionsDialog.newInstance(mStackLevel);
        newFragment.show(ft, "advancedDialog");
    }
}