package com.carsense.usbstringreader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

public class SettingsLogger extends AppCompatActivity {

    EditText edtNumOfBytes;
    Button btnNumOfBytes;

    RadioGroup rdBtnOptions;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_logger);

        rdBtnOptions = (RadioGroup) findViewById(R.id.question1);
        rdBtnOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rd_dollar_star) {
                    Constants.IS_DOLLAR_STAR_CONDITION = true;
                    MainActivity.prevString = "";
                } else if (checkedId == R.id.rd_bytes) {
                    Constants.IS_DOLLAR_STAR_CONDITION = false;
                    MainActivity.prevString = "";
                }
            }
        });
        if (Constants.IS_DOLLAR_STAR_CONDITION) {
            rdBtnOptions.check(R.id.rd_dollar_star);
        } else {
            rdBtnOptions.check(R.id.rd_bytes);
        }


        edtNumOfBytes = (EditText) findViewById(R.id.edt_num_of_bytes);
        edtNumOfBytes.setText(Constants.MAX_NUMBER_OF_BYTES + "");
        btnNumOfBytes = (Button) findViewById(R.id.btn_num_of_bytes);
        btnNumOfBytes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Constants.MAX_NUMBER_OF_BYTES = Integer.parseInt(edtNumOfBytes.getText() + "");
                    Toast.makeText(getApplicationContext(), "Bytes : " + Constants.MAX_NUMBER_OF_BYTES,
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Enter valid integer.",
                            Toast.LENGTH_LONG).show();
                }

            }
        });
    }
}
