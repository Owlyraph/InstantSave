package com.example.instantsave;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingsActivity extends AppCompatActivity {
    private EditText mNameField, mPhoneField, mAmbulanceField;
    private Button mBack, mConfirm;

    private ImageView mProfileImage;

    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabase;

    private String userID;
    private String mName;
    private String mPhone;
    private String mAmbulance;
    private String mProfileImageUrl;
    private String mService;

    private Uri resultUri;
    private RadioGroup mRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings);

        mNameField=(EditText) findViewById(R.id.name);
        mPhoneField=(EditText) findViewById(R.id.phone);
        mAmbulanceField=(EditText) findViewById(R.id.ambulance);

        mProfileImage=(ImageView) findViewById(R.id.profileImage);
        mRadioGroup=(RadioGroup) findViewById(R.id.radioGroup);

        mBack=(Button) findViewById(R.id.back);
        mConfirm=(Button) findViewById(R.id.confirm);

        mAuth=FirebaseAuth.getInstance();
        userID= mAuth.getCurrentUser().getUid();
        mDriverDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child("Ambulance Drivers").child(userID);

        getUserInfo();

        mProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });

        mConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserInformation();
            }
        });
        mBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                return;
            }
        });
    }

    private void getUserInfo(){
        mDriverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map<String, Object> map=(Map<String, Object>) snapshot.getValue();

                    if (map.get("name")!=null){
                        mName=map.get("name").toString();
                        mNameField.setText(mName);
                    }
                    if (map.get("phone")!=null){
                        mPhone=map.get("phone").toString();
                        mPhoneField.setText(mPhone);
                    }
                    if (map.get("ambulance")!=null){
                        mAmbulance=map.get("ambulance").toString();
                        mAmbulanceField.setText(mAmbulance);
                    }
                    if (map.get("service")!=null){
                        mService=map.get("service").toString();
                        switch (mService){
                            case "St_John":
                                mRadioGroup.check(R.id.St_John);
                                break;
                            case "Amref":
                                mRadioGroup.check(R.id.Amref);
                                break;
                            case "Eplus":
                                mRadioGroup.check(R.id.Eplus);
                                break;
                        }
                    }
                    if (map.get("profileImageUrl")!=null){
                        mProfileImageUrl=map.get("profileImageUrl").toString();
                        Glide.with(getApplication()).load(mProfileImageUrl).into(mProfileImage);
                    }


                }


            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void saveUserInformation() {

        mName= mNameField.getText().toString();
        mPhone= mPhoneField.getText().toString();
        mAmbulance= mAmbulanceField.getText().toString();

        int selectId=mRadioGroup.getCheckedRadioButtonId();

        final RadioButton radioButton=(RadioButton) findViewById(selectId);
        if (radioButton.getText() == null){
            return;
        }

        mService=radioButton.getText().toString();

        Map userInfo=new HashMap();
        userInfo.put("name",mName);
        userInfo.put("phone",mPhone);
        userInfo.put("ambulance",mAmbulance);
        userInfo.put("service",mService);
        mDriverDatabase.updateChildren(userInfo);

        if (resultUri !=null){
            StorageReference filepath= FirebaseStorage.getInstance().getReference().child("profile_images").child(userID);
            Bitmap bitmap=null;
            try {
                bitmap= MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), resultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20,baos);
            byte [] data= baos.toByteArray();
            UploadTask uploadTask=filepath.putBytes(data);

            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    finish();
                    return;
                }
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    if (taskSnapshot.getMetadata() != null){
                        if (taskSnapshot.getMetadata().getReference() != null){
                            Task<Uri>result = taskSnapshot.getStorage().getDownloadUrl();
                            result.addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    String downloadUrl=uri.toString();
                                    Map newImage=new HashMap();
                                    newImage.put("profileImageUrl", downloadUrl);
                                    mDriverDatabase.updateChildren(newImage);


                                }
                            });
                            finish();
                            return;
                        }
                    }
                }
            });
        }else{
            finish();
        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode ==1 && resultCode == Activity.RESULT_OK){
            final Uri imageUri= data.getData();
            resultUri= imageUri;
            mProfileImage.setImageURI(resultUri);
        }
    }
}
