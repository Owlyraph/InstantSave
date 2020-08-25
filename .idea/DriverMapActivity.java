package com.example.instantsave;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.solver.widgets.Snapshot;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, RoutingListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private Button mLogout,mSettings, mHelpStatus;

    private int status=0;

    private String clientId="", destination;
    private LatLng destinationLatLong;
    private Boolean isLoggingOut=false;
    private LinearLayout mClientInfo;
    private ImageView mClientProfileImage;
    private TextView mClientName, mClientPhone, mClientDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        polylines = new ArrayList<>();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else {
            mapFragment.getMapAsync(this);
        }

        FusedLocationProviderClient fusedLocationProviderClient=LocationServices.getFusedLocationProviderClient(getApplicationContext());

        mClientInfo=(LinearLayout) findViewById(R.id.clientInfo);
        mClientProfileImage=(ImageView) findViewById(R.id.clientProfileImage);
        mClientName=(TextView) findViewById(R.id.clientName);
        mClientPhone=(TextView) findViewById(R.id.clientPhone);
        mClientDestination=(TextView) findViewById(R.id.clientDestination);

        mSettings=(Button) findViewById(R.id.settings);
        mLogout=(Button) findViewById(R.id.Logout);
        mHelpStatus=(Button) findViewById(R.id.helpStatus);
        mHelpStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (status){
                    case 1:
                        status=2;
                        erasePolylines();
                        if (destinationLatLong.latitude!=0.0 && destinationLatLong.longitude!=0.0){
                            getRouteToMarker(destinationLatLong);
                        }
                        mHelpStatus.setText("help successful");
                        break;
                    case 2:
                        recordHelp();
                        endHelp();
                        break;
                }
            }
        });
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggingOut=true;

                disconnectAmbulance();
                FirebaseAuth.getInstance().signOut();
                Intent intent=new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        getAssignedClient();
    }

    private void getAssignedClient(){
        String ambulanceDriverId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedClientRef=FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(ambulanceDriverId).child("clientRequest").child("clientHelpId");
        assignedClientRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                        status=1;
                        clientId=snapshot.getValue().toString();
                        getAssignedClientPickUpLocation();
                        getAssignedClientDestination();
                        getAssignedClientInfo();

                }else {
                   endHelp();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }

    Marker pickupMarker;
    private DatabaseReference assignCustomerPickupLocationRef;
    private ValueEventListener assignCustomerPickupLocationRefListener;
    private void getAssignedClientPickUpLocation(){
        assignCustomerPickupLocationRef= FirebaseDatabase.getInstance().getReference().child("ClientRequest").child(clientId).child("l");
        assignCustomerPickupLocationRefListener= assignCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() &&!clientId.equals("")){
                   List<Object> map=(List<Object>) snapshot.getValue();
                    double locationLat=0;
                    double locationLong=0;
                    if (map.get(0) !=null){
                        locationLat=Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) !=null){
                        locationLong=Double.parseDouble(map.get(1).toString());
                    }
                    LatLng pickupLatLong=new LatLng(locationLat,locationLong);

                    pickupMarker=mMap.addMarker(new MarkerOptions().position(pickupLatLong).title("pickup location"));
                    getRouteToMarker(pickupLatLong);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }

    private void getRouteToMarker(LatLng pickupLatLong) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLong)
                .build();
        routing.execute();
    }

    private void getAssignedClientDestination(){
        String ambulanceDriverId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedClientRef=FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(ambulanceDriverId).child("clientRequest");
        assignedClientRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if (map.get("destination")!=null){
                        destination=map.get("destination").toString();
                        mClientDestination.setText("Destination: "+ destination);
                    }
                    else {
                        mClientDestination.setText("Destination: --");
                    }

                    Double destinationLat=0.0;
                    Double destinationLong=0.0;
                    if (map.get("destinationLat")!=null){
                        destinationLat=Double.valueOf(map.get("destinationLat").toString());
                    }
                    if (map.get("destinationLong")!=null){
                        destinationLong=Double.valueOf(map.get("destinationLong").toString());
                        destinationLatLong=new LatLng(destinationLat, destinationLong);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }

    private void getAssignedClientInfo(){
        mClientInfo.setVisibility(View.VISIBLE);
       DatabaseReference mClientDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child("Clients").child(clientId);
        mClientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map<String, Object> map=(Map<String, Object>) snapshot.getValue();

                    if (map.get("name")!=null){

                        mClientName.setText(map.get("name").toString());
                    }
                    if (map.get("phone")!=null){

                        mClientPhone.setText(map.get("phone").toString());
                    }
                    if (map.get("profileImageUrl")!=null){

                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mClientProfileImage);
                    }


                }


            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    private void endHelp() {
        mHelpStatus.setText("picked client");
        erasePolylines();

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference ambulanceRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(userId).child("ClientRequest");
            ambulanceRef.removeValue();

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ClientRequest");
            GeoFire geoFire = new GeoFire(ref);
            geoFire.removeLocation(clientId);
            clientId="";

            if (pickupMarker != null) {
                pickupMarker.remove();
            }
        if (assignCustomerPickupLocationRefListener !=null){
            assignCustomerPickupLocationRef.removeEventListener(assignCustomerPickupLocationRefListener);
        }

        mClientInfo.setVisibility(View.GONE);
        mClientName.setText("");
        mClientPhone.setText("");
        mClientDestination.setText("Destination: --");
        mClientProfileImage.setImageResource(R.mipmap.default_user);
    }

    private void recordHelp(){
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ambulanceRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(userId).child("history");
        DatabaseReference clientRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Clients").child(clientId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("Users").child("history");
        String requestId=historyRef.push().getKey();
        ambulanceRef.child(requestId).setValue(true);
        clientRef.child(requestId).setValue(true);

        HashMap map=new HashMap();
        map.put("ambulance",userId);
        map.put("client",clientId);
        map.put("rating",0);
        map.put("timestamp", getCurrentTimestamp());
        historyRef.child(requestId).updateChildren(map);


    }

    private Long getCurrentTimestamp() {
        Long timestamp= System.currentTimeMillis()/1000;
        return timestamp;
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
        if( getApplicationContext() !=null){

            mLastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("ambulanceAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("ambulanceWorking");
            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);




            switch (clientId){
                case "":
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;

                    default:
                        geoFireAvailable.removeLocation(userId);
                        geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));


                        break;
            }



        }

    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest= new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest,this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    private void disconnectAmbulance(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref= FirebaseDatabase.getInstance().getReference("Ambulances Available");
        GeoFire geoFire=new GeoFire(ref);
        geoFire.removeLocation(userId);
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
        if (!isLoggingOut){
            disconnectAmbulance();
        }

    }
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingCancelled() {

    }
    private void erasePolylines(){
        for (Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
