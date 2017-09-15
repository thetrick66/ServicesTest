package com.galeno.patricio.sevicetest;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {
    /**
     * Text views para los datos
     */
    private boolean father = false;
    private boolean creado = false;
    private boolean enlazado = false;
    MemoryService mService;
    boolean mBound = false;
    Intent intentMemoryService;
    private TextView memoryUsageText;
    private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtener las etiquetas
        memoryUsageText = (TextView) findViewById(R.id.memory_ava_text);
        //progressText = (TextView) findViewById(R.id.progress_text);

        // Obtener botón de monitoreo de memoria
        ToggleButton button = (ToggleButton) findViewById(R.id.toggleButton);

        intentMemoryService = new Intent(
                getApplicationContext(), MemoryService.class);
        if(!isMyServiceRunning(MemoryService.class)){
            creado = true;
            father = true;
            startService(intentMemoryService); //Iniciar servicio
            Toast.makeText(getApplicationContext(), "Servicio Creado! ", Toast.LENGTH_SHORT).show();
            button.setChecked(true);
            button.setText("DETENER SERVICE");
            button.setTextOff("INICIAR SERVICE");
            button.setTextOn("DETENER SERVICE");
        }
        else{
            Toast.makeText(getApplicationContext(), "Servicio ya Corriendo! ", Toast.LENGTH_SHORT).show();
            button.setText("ENLAZAR SERVICE");
            button.setTextOff("ENLAZAR SERVICE");
            button.setTextOn("DESENLAZAR SERVICE");
        }
        // Setear escucha de acción
        button.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        if (isChecked) {
                            //Toast.makeText(getApplicationContext(), "A conectarse! ", Toast.LENGTH_SHORT).show();
                            if(!father){
                                Toast.makeText(getApplicationContext(), "Enlazándose! ", Toast.LENGTH_SHORT).show();
                                bindService(intentMemoryService,mConnection,Context.BIND_AUTO_CREATE);
                                enlazado = true;
                            }
                            else{
                                creado = true;
                                father = true;
                                startService(intentMemoryService); //Iniciar servicio
                                Toast.makeText(getApplicationContext(), "Servicio Creado! ", Toast.LENGTH_SHORT).show();
                            }

                        } else {
                            if(father){
                                Toast.makeText(getApplicationContext(), "Deteniendo Servicio! ", Toast.LENGTH_SHORT).show();
                                stopService(intentMemoryService);
                                creado = false;
                            }
                            else{
                                memoryUsageText.setText("Memoria");
                                Toast.makeText(getApplicationContext(), "Desenlazando! ", Toast.LENGTH_SHORT).show();
                                unbindService(mConnection);
                                enlazado = false;
                            }
                        }
                    }
                }
        );


        // Filtro de acciones que serán alertadas
        IntentFilter filter = new IntentFilter(
                Constants.ACTION_RUN_ISERVICE);
        filter.addAction(Constants.ACTION_RUN_SERVICE);
        filter.addAction(Constants.ACTION_MEMORY_EXIT);
        filter.addAction(Constants.ACTION_PROGRESS_EXIT);

        // Crear un nuevo ResponseReceiver
        ResponseReceiver receiver =
                new ResponseReceiver();
        // Registrar el receiver y su filtro
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                filter);
    }

    /**
     * Método onClick() personalizado para {@code turn_intent_service}
     * @param v View presionado
     */
    /*
    public void onClickTurnIntentService(View v) {
        Intent intent = new Intent(this, ProgressIntentService.class);
        intent.setAction(Constants.ACTION_RUN_ISERVICE);
        startService(intent);
    }
    */
    @Override
    protected void onDestroy() {
        if(enlazado){
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    // Broadcast receiver que recibe las emisiones desde los servicios
    private class ResponseReceiver extends BroadcastReceiver {

        // Sin instancias
        private ResponseReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Constants.ACTION_RUN_SERVICE:
                    if(creado || enlazado){
                        memoryUsageText.setText(intent.getStringExtra(Constants.EXTRA_MEMORY));
                    }

                    break;

                case Constants.ACTION_RUN_ISERVICE:
                    progressText.setText(intent.getIntExtra(Constants.EXTRA_PROGRESS, -1) + "");
                    break;

                case Constants.ACTION_MEMORY_EXIT:
                    memoryUsageText.setText("Memoria");
                    break;

                case Constants.ACTION_PROGRESS_EXIT:
                    progressText.setText("Progreso");
                    break;
            }
        }
    }
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MemoryService.LocalBinder binder = (MemoryService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}