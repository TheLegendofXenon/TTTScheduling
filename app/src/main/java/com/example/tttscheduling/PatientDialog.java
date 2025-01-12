package com.example.tttscheduling;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;

public class PatientDialog extends AppCompatActivity implements AppointmentAdapter.OnItemClickListener, DeleteApptDialog.DeleteDialogListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
    // Declarations and Initializations
    private Button backBtn;
    private String name, email, phone, SSN, DOB, date, time, address, adminEmail = "", adminAddress = "", holdDateCheck = "", holdTimeCheck = "";
    private TextView emailText, phoneText, SSNText, DOBText;
    private int deletePos, editPos, day, month, year, hr, min, dayFinal, monthFinal, yearFinal, hrFinal, minFinal;

    private RecyclerView aList;
    private ArrayList<AppointmentModel> appointmentList;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

    private FirebaseFirestore fStore = FirebaseFirestore.getInstance();
    private FirebaseAuth fAuth = FirebaseAuth.getInstance();
    private CollectionReference adminListRef = fStore.collection("Admin");
    private Query apptListRef = fStore.collection("Appointments").orderBy("Date", Query.Direction.ASCENDING)
            .orderBy("Time", Query.Direction.ASCENDING);
    private DocumentReference editA, checkA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_dialog);

        // Initializations
        backBtn = findViewById(R.id.patientListDialogBack);
        emailText = findViewById(R.id.pListDialogEmail);
        phoneText = findViewById(R.id.pListDialogPhone);
        SSNText = findViewById(R.id.pListDialogSSN);
        DOBText = findViewById(R.id.pListDialogDOB);
        aList = findViewById(R.id.pListApptList);

        final Intent retrieve = getIntent();
        final int checkPP = retrieve.getIntExtra("ParentPage", 0);
        name = retrieve.getStringExtra("Name");
        email = retrieve.getStringExtra("Email");
        phone = retrieve.getStringExtra("Phone");
        SSN = retrieve.getStringExtra("SSN");
        DOB = retrieve.getStringExtra("DOB");

        getSupportActionBar().setTitle(name);
        emailText.setText("Email: " + email);
        phoneText.setText("Phone Number: " + phone);
        SSNText.setText("Last 4 Digits of SSN: " + SSN);
        DOBText.setText("Date of Birth: " + DOB);

        // Get the admin's address.
        if (checkPP == 0) {
            adminListRef.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(QuerySnapshot queryDocumentSnapshots, FirebaseFirestoreException e) {
                    if (e != null) {
                        return;
                    }

                    adminEmail = fAuth.getCurrentUser().getEmail();
                    for (QueryDocumentSnapshot adminSnapshot : queryDocumentSnapshots) {
                        AdminModel admin = adminSnapshot.toObject(AdminModel.class);

                        // Get the right admin's address
                        if ((admin.getEmail()).equals(adminEmail)) {
                            adminAddress = admin.getAddress();
                            break;
                        }
                    }
                }
            });
        }

        // Create an appointment list
        apptListRef.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot queryDocumentSnapshots, FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                appointmentList = new ArrayList<>();
                for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                    AppointmentModel appointment = documentSnapshot.toObject(AppointmentModel.class);

                    // Get the right appointments for the current patient (depends upon which PatientDialog).
                    if (checkPP == 0 && email.equals(appointment.getEmail()) && adminAddress.equals(appointment.getAddress())) {
                        date = appointment.getDate();
                        time = appointment.getTime();
                        address = appointment.getAddress();
                        appointmentList.add(new AppointmentModel(name, email, phone, date, time, DOB, address));
                    }
                    else if (checkPP == 1 && email.equals(appointment.getEmail())) {
                        date = appointment.getDate();
                        time = appointment.getTime();
                        address = appointment.getAddress();
                        appointmentList.add(new AppointmentModel(name, email, phone, date, time, DOB, address));
                    }
                }

                aList.setHasFixedSize(true);
                adapter = new AppointmentAdapter(appointmentList);
                ((AppointmentAdapter) adapter).setOnItemClickListener(PatientDialog.this);

                aList.setAdapter(adapter);
                aList.setLayoutManager(layoutManager);
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPP == 0) // Go to Patient List
                    goPListBack();
                else if (checkPP == 1) // Go to Patient Home
                    goPHomeBack();
            }
        });
    }

    // This method redirects to the PatientList activity after pressing the back button.
    public void goPListBack() {
        startActivity(new Intent(getApplicationContext(), PatientList.class));
    }

    // This method redirects to the PatientHome activity after pressing the back button.
    private void goPHomeBack() {
        startActivity(new Intent(getApplicationContext(), PatientHome.class));
    }

    // This method does nothing, but is required for deleteClick and editClick to work.
    @Override
    public void itemClick(int pos) {
    }

    // This method deletes an appointment.
    @Override
    public void deleteClick(int pos) {
        deletePos = pos;
        DeleteApptDialog dialog = new DeleteApptDialog();
        dialog.show(getSupportFragmentManager(), "example dialog");
    }

    // This helper method displays a dialog box for deleting an appointment.
    @Override
    public void onYesClicked() {
        // Find the appointment in Firestore and delete.
        final AppointmentModel deleteAppt = appointmentList.get(deletePos);
        final String deleteApptDate = deleteAppt.getDate();
        final String deleteApptTime = deleteAppt.getTime();
        final String deleteApptAddress = deleteAppt.getAddress();

        // Delete the patient's appointment
        apptListRef.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot queryApptSnapshots, FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                for (QueryDocumentSnapshot deleteApptSnapshot : queryApptSnapshots) {
                    AppointmentModel apptDelete = deleteApptSnapshot.toObject(AppointmentModel.class);
                    String daDateCheck = apptDelete.getDate(); String daTimeCheck = apptDelete.getTime();
                    DocumentReference deleteA = deleteApptSnapshot.getReference();
                    if (deleteApptDate.equals(daDateCheck) && deleteApptTime.equals(daTimeCheck)) {
                        deleteA.delete();
                        Toast.makeText(PatientDialog.this, "Appointment Successfully Canceled!", Toast.LENGTH_SHORT).show();

                        // Send a cancellation email
                        String tempAMorPM = deleteApptTime.substring(0, 2);
                        String tempTime = deleteApptTime.substring(3);
                        String emailSubject = "Cancellation of appointment set for " + deleteApptDate + " at " + deleteApptAddress;
                        String emailMessage = "Hello " + name + ",\n\n" +
                                "Your appointment scheduled for " + deleteApptDate + " at " + tempTime + ' ' + tempAMorPM + " at "
                                + deleteApptAddress + " has been cancelled..." + "\n\nThank you,\n" + "TT&T Scheduling";
                        JavaMailAPI sendEmail = new JavaMailAPI(PatientDialog.this, deleteAppt.getEmail(), emailSubject, emailMessage);
                        sendEmail.execute();

                        break;
                    }
                }
            }
        });

        appointmentList.remove(deletePos);
        adapter.notifyItemRemoved(deletePos);
    }

    // This method edits an appointment.
    @Override
    public void editClick(int pos) {
        editPos = pos;

        Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(PatientDialog.this, PatientDialog.this, year, month, day);
        datePickerDialog.show();
    }

    @Override
    public void onDateSet(DatePicker view, int i, int i1, int i2) {
        yearFinal = i;
        monthFinal = i1 + 1;
        dayFinal = i2;

        Calendar c = Calendar.getInstance();
        hr = c.get(Calendar.HOUR_OF_DAY);
        min = c.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(PatientDialog.this, PatientDialog.this, hr, min, DateFormat.is24HourFormat(this));
        timePickerDialog.show();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        String amOrPM = "";

        // Set the correct times to AM or PM instead of 24-hour.
        hrFinal = hourOfDay;
        if (hrFinal > 12) {
            hrFinal -= 12;
            amOrPM = "PM";
        }
        else if (hrFinal == 12) {
            amOrPM = "PM";
        }
        else if (hrFinal > 0) {
            amOrPM = "AM";
        }
        else {
            hrFinal += 12;
            amOrPM = "AM";
        }

        minFinal = minute;

        final String tempDate = monthFinal + "/" + dayFinal + "/" + yearFinal;
        final String tempTime = amOrPM + " " + hrFinal + ":" + String.format("%02d",minFinal);

        // Find the appointment in Firestore and delete.
        final AppointmentModel editAppt = appointmentList.get(editPos);
        final String editApptDate = editAppt.getDate();
        final String editApptTime = editAppt.getTime();
        final String editApptAddress = editAppt.getAddress();

        if (!(minFinal % 10 == 0)) {// Checks to make sure that a 10-min mark is chosen.
            Toast.makeText(PatientDialog.this, "Please choose a 10-minute mark...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send a reschedule email
        String tAMorPM = tempTime.substring(0, 2);
        String tTime = tempTime.substring(3);
        String emailSubject = "Reschedule of appointment set for " + tempDate + " at " + editApptAddress;
        String emailMessage = "Hello " + name + ",\n\n" +
                "Your appointment has been rescheduled to " + tempDate + " at " + tTime + ' ' + tAMorPM + " at "
                + editApptAddress + '.' + "\n\nThank you,\n" + "TT&T Scheduling";
        JavaMailAPI sendEmail = new JavaMailAPI(PatientDialog.this, editAppt.getEmail(), emailSubject, emailMessage);
        sendEmail.execute();

        Toast.makeText(PatientDialog.this, "Appointment Successfully Rescheduled!", Toast.LENGTH_SHORT).show();
        adapter.notifyItemChanged(editPos);
    }

    private class AppointmentViewHolder extends RecyclerView.ViewHolder {
        TextView list_name, list_email, list_phone, list_DOB, list_time, list_address;

        public AppointmentViewHolder(@NonNull View itemView) {
            super(itemView);

            list_name = (TextView) findViewById(R.id.alist_name);
            list_email = (TextView) findViewById(R.id.alist_email);
            list_phone = (TextView) findViewById(R.id.alist_phone);
            list_DOB = (TextView) findViewById(R.id.alist_DOB);
            list_time = (TextView) findViewById(R.id.alist_time);
            list_address = (TextView) findViewById(R.id.alist_address);
        }
    }
}
