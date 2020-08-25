package com.example.instantsave;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private Button mLogout, mRequest, mSettings, mHistory;
    private LatLng pickupLocation;
    private Boolean requestBol=false;
    private Marker pickupMarker;
    private String destination, requestService;
    private LatLng destinationLatLong;
    private LinearLayout mDriverInfo;
    private ImageView mDriverProfileImage;
    private TextView mDriverName, mDriverPhone, mAmbulance;
    private RadioGroup mRadioGroup;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map); if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ClientMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else {
            mapFragment.getMapAsync(this);
        }

        destinationLatLong=new LatLng(0.0, 0.0);

        mDriverInfo=(LinearLayout) findViewById(R.id.driverInfo);
        mDriverProfileImage=(ImageView) findViewById(R.id.driverProfileImage);
        mDriverName=(TextView) findViewById(R.id.driverName);
        mDriverPhone=(TextView) findViewById(R.id.driverPhone);
        mAmbulance=(TextView) findViewById(R.id.ambulance);

        mRadioGroup=(RadioGroup) findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.Eplus);
        mLogout=(Button) findViewById(R.id.Logout);
        mRequest=(Button) findViewById(R.id.request);
        mSettings=(Button) findViewById(R.id.settings);
        mHistory=(Button) findViewById(R.id.history);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent=new Intent(ClientMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestBol){
                    endHelp();

                }else{
                    int selectId=mRadioGroup.getCheckedRadioButtonId();

                    final RadioButton radioButton=(RadioButton) findViewById(selectId);
                    if (radioButton.getText() == null){
                        return;
                    }

                    requestService=radioButton.getText().toString();

                    requestBol=true;
                    String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref=FirebaseDatabase.getInstance().getReference("ClientRequest");
                    GeoFire geoFire= new GeoFire(ref);
                    geoFire.setLocation(userId,new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()));

                    pickupLocation= new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude());
                    pickupMarker=mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Here"));

                    mRequest.setText("Getting you Help....");

                    getClosestAmbulance();
                }

            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(ClientMapActivity.this, ClientSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(ClientMapActivity.this, HistoryActivity.class);
                intent.putExtra("clientOrAmbulance","Clients");
                startActivity(intent);
                return;
            }
        });

        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME));

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NotNull Place place) {
                // TODO: Get info about the selected place.
                destination=place.getName().toString();
                destinationLatLong=place.getLatLng();

            }


            @Override
            public void onError(@NotNull Status status) {
                // TODO: Handle the error.
            }
        });

    }
    private int radius=1;
    private Boolean ambulanceFound=false;
    private DataSnapshot ambulanceFoundID;
    GeoQuery geoQuery;
    private void getClosestAmbulance(){
        DatabaseReference ambulanceAvailable=FirebaseDatabase.getInstance().getReference().child("Ambulances Available");

        GeoFire geoFire= new GeoFire(ambulanceAvailable);
        geoQuery=geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryDataEventListener(new GeoQueryDataEventListener() {
            @Override
            public void onDataEntered(DataSnapshot dataSnapshot, GeoLocation location) {
                if (!ambulanceFound && requestBol){
                    DatabaseReference mClientDatabase=FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(String.valueOf(dataSnapshot));
                    mClientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                                Map<String, Object> ambulancemap=(Map<String, Object>) snapshot.getValue();

                                if (ambulanceFound){
                                    return;
                                }
                                if (ambulancemap.get("service").equals(requestService)){
                                    ambulanceFound=true;
                                    ambulanceFoundID= dataSnapshot;
                                    DatabaseReference ambulanceRef=FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(String.valueOf(ambulanceFoundID)).child("clientRequest");
                                    String clientId=FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map=new HashMap();
                                    map.put("clientHelpId",clientId);
                                    map.put("destination",destination);
                                    map.put("destinationLat",destinationLatLong.latitude);
                                    map.put("destinationLong",destinationLatLong.longitude);
                                    ambulanceRef.updateChildren(map);

                                    getAmbulanceLocation();
                                    getAmbulanceInfo();
                                    getHasHelpEnded();
                                    mRequest.setText("Looking for Ambulance Location....");

                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });

                }

            }

            @Override
            public void onDataExited(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onDataMoved(DataSnapshot dataSnapshot, GeoLocation location) {

            }

            @Override
            public void onDataChanged(DataSnapshot dataSnapshot, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!ambulanceFound){
                    radius++;
                    getClosestAmbulance();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
    private Marker mAmbulanceMarker;
    private DatabaseReference ambulanceLocationRef;
    private ValueEventListener ambulanceLocationRefListener;
    private void getAmbulanceLocation(){
        ambulanceLocationRef=FirebaseDatabase.getInstance().getReference().child("ambulanceWorking").child(String.valueOf(ambulanceFoundID)).child("l");
        ambulanceLocationRefListener= ambulanceLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()&& requestBol){
                    List<Object> map=(List<Object>) snapshot.getValue();
                    double locationLat=0;
                    double locationLong=0;
                    mRequest.setText("Ambulance Found!");
                    if (map.get(0) !=null){
                        locationLat=Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) !=null){
                        locationLong=Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLong=new LatLng(locationLat,locationLong);
                    if (mAmbulanceMarker !=null){
                        mAmbulanceMarker.remove();
                    }
                    Location loc1= new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2= new Location("");
                    loc2.setLatitude(driverLatLong.latitude);
                    loc2.setLongitude(driverLatLong.longitude);

                    float distance=loc1.distanceTo(loc2);

                    if (distance<100){
                        mRequest.setText("Ambulance Is Here!");
                    }else{
                        mRequest.setText("Ambulance Is Here!"+String.valueOf(distance));
                    }



                    mAmbulanceMarker= mMap.addMarker(new MarkerOptions().position(driverLatLong).title("your ambulance"));


                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }
    private void getAmbulanceInfo(){
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mClientDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(String.valueOf(ambulanceFoundID));
        mClientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map<String, Object> map=(Map<String, Object>) snapshot.getValue();

                    if (map.get("name")!=null){

                        mDriverName.setText(map.get("name").toString());
                    }
                    if (map.get("phone")!=null){

                        mDriverPhone.setText(map.get("phone").toString());
                    }
                    if (map.get("ambulance")!=null){

                        mAmbulance.setText(map.get("ambulance").toString());
                    }
                    if (map.get("profileImageUrl")!=null){

                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mDriverProfileImage);
                    }


                }


            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private DatabaseReference helpHasEndedRef;
    private ValueEventListener helpHasEndedRefListener;
    private void getHasHelpEnded(){
        helpHasEndedRef= FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(String.valueOf(ambulanceFoundID)).child("clientRequest").child("clientHelpId");
        helpHasEndedRefListener= helpHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){

                }else {
                    endHelp();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }

    private void endHelp() {
        requestBol = false;
        geoQuery.removeAllListeners();
        ambulanceLocationRef.removeEventListener(ambulanceLocationRefListener);
        helpHasEndedRef.removeEventListener(helpHasEndedRefListener);

        if (ambulanceFoundID != null) {
            DatabaseReference ambulanceRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(String.valueOf(ambulanceFoundID)).child("ClientRequest");
            ambulanceRef.removeValue();
            ambulanceFoundID = null;
        }
        ambulanceFound = false;
        radius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ClientRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if (pickupMarker != null) {
            pickupMarker.remove();
        }if (mAmbulanceMarker !=null){
            mAmbulanceMarker.remove();
        }
        mRequest.setText("Call Ambulance");

        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhone.setText("");
        mAmbulance.setText("");
        mDriverProfileImage.setImageResource(R.mipmap.default_user);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=PackageManager.PERMISSION_GRANTED) {

            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }
    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient=new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation= location;

        LatLng latLng= new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));


    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest= new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ClientMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest,this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    final int LOCATION_REQUEST_CODE=1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATION_REQUEST_CODE:{
                if (grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){

                    SupportMapFragment mapFragment = new SupportMapFragment();
                    mapFragment.getMapAsync(this);
                }else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}


