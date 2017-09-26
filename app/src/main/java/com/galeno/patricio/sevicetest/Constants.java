package com.galeno.patricio.sevicetest;

/**
 * Created by Patricio on 31-08-2017.
 */

/**
 * Contiene las constantes de las acciones de los servicios y sus parámetros
 */
public class Constants {
    /**
     * Constantes para {@link BTService}
     */
    public static final String ACTION_RUN_SERVICE = "com.galeno.patricio.action.RUN_SERVICE";
    public static final String ACTION_MEMORY_EXIT = "com.galeno.patricio.action.MEMORY_EXIT";

    public static final String EXTRA_MEMORY = "com.herprogramacion.memoryout.extra.MEMORY";

    /**
     * Constantes para {@link ProgressIntentService}
     */
    public static final String ACTION_RUN_ISERVICE = "com.herprogramacion.memoryout.action.RUN_INTENT_SERVICE";
    public static final String ACTION_PROGRESS_EXIT = "com.herprogramacion.memoryout.action.PROGRESS_EXIT";

    public static final String EXTRA_PROGRESS = "com.herprogramacion.memoryout.extra.PROGRESS";

}