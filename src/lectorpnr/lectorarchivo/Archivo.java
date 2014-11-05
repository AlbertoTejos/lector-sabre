package lectorpnr.lectorarchivo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import lectorpnr.datos.db.ArchivoDAO;
import lectorpnr.datos.db.Parametros;

/**
 *
 * @author Alberto
 */
public class Archivo {

    private File archivo;
    private String numeroPnr;
    private String fechaEmision;
    private String moneda;
    private double valorNeto;
    private double valorFinal;
    private double valorTasas;
    private double valorTipoDeCambio;
    private String numeroFile;
    private String fechaAnulacion;
    private String fechaRemision;
    private String ruta;
    private String tipo;
    private String estado;
    private ArrayList<Ticket> pajaseros;
    private ArrayList<Segmento> segmentos;
    private final ArrayList<String> bf;


    public Archivo(File archivo) throws FileNotFoundException, IOException {
        this.archivo = archivo;
        this.numeroPnr = "";
        this.fechaEmision = "";
        this.moneda = "";
        this.valorNeto = 0.0;
        this.valorFinal = 0.0;
        this.valorTasas = 0.0;
        this.numeroFile = "";
        this.fechaAnulacion = "";
        this.fechaRemision = "";
        this.ruta = "";
        this.tipo = "TKT";
        this.estado = "";
        this.valorTipoDeCambio = 0.0;
        this.pajaseros = new ArrayList<>();
        this.segmentos = new ArrayList<>();
        this.bf = new ArrayList<>();
        initReader();
        this.iniciarArchivo();

    }

    private void initReader() throws FileNotFoundException, IOException {
        try (BufferedReader bfi = new BufferedReader(new FileReader(archivo))) {
            String cadena;
            while ((cadena = bfi.readLine()) != null) {
                if (!cadena.equals("")) {
                    bf.add(cadena);
                }
            }
            System.out.println("Contenido del archivo: " + bf);
        }
    }

    private void iniciarArchivo() throws FileNotFoundException, IOException {
               
        //Indica el tipo de transacción
        final String verificacion = getLineaString(getCharsLinea(0), 14, 1);

        //5 = Ticket vacío
        //C = EMD vacío
        
        //Ignoramos los tickets y emd vacíos
        if(!verificacion.equalsIgnoreCase("5") && !verificacion.equalsIgnoreCase("C")){
            iniciarArchivoTicket();
        }else{
            iniciarAnulacion();
        }
        
    }

    public void iniciarAnulacion() throws FileNotFoundException, IOException {
        
        final int CANTIDAD_TICKETS = Integer.parseInt(getLineaString(getCharsLinea(0), 41, 2));
        
        this.setEstado("ANULADO");
        for (int i = 1; i <= CANTIDAD_TICKETS; i++) {           
            Ticket tic = new Ticket();
            tic.setTicket(getLineaString(getCharsLinea(0), 27, 14));
            this.pajaseros.add(tic);
        }
    }

    private void iniciarArchivoTicket() throws FileNotFoundException, IOException {
        
        //Constantes
        final String FECHA_EMISION = getLineaString(getCharsLinea(0), 117, 5);
        
        //Formato yyyy-mmm-dd (Usuario SQL en español)
        this.setFechaEmision(getFechaSQL(FECHA_EMISION));
        this.setNumeroPnr(getLineaString(getCharsLinea(0), 54, 8));
        
        //1 = Solo ticket
        //A = Solo EMD
        //B = EMD asociado con un número de ticket   
        
        final boolean esTicket = getLineaString(getCharsLinea(0), 14, 1).equals("1");
        final boolean esEMD = getLineaString(getCharsLinea(0), 14, 1).equals("A");

        //Cantidad de incidencias
        final int CANTIDAD_PERSONAS = getIncidencias("M1");
        int CANTIDAD_SEGMENTOS = getIncidencias("M3"); 
        //Linea MG con los datos del EMD
        final int LINEA_EMD_DATOS = getIndexLinea("MG");
        
        if(esTicket && CANTIDAD_PERSONAS > 0){
            for (int i = 1; i <= CANTIDAD_PERSONAS; i++) {
                String identificadorDelTicket = "M2"+0+i;
                String identificadorDatosPersonas = "M1"+0+i;
                int lineaFormaPago = (getIndexLinea(identificadorDelTicket)+3);
                int cantidadDeTickets = getIncidencias(identificadorDelTicket);
                int cantidadDePersonas = getIncidencias(identificadorDatosPersonas);
                for (int j = 0; j < cantidadDePersonas; j++) {
                    Ticket tic = new Ticket();
                    tic.setNombrePasajero(getLineaString(getCharsLinea(getIndexLinea(identificadorDatosPersonas)), 5, 64));
                    for (int k = 0; k < cantidadDeTickets; k++) {
                        tic.setTicket(getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 234, 10));
                        tic.setTipoPasajero(getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 5, 3));
                        tic.setfPago(getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 20, 1));
                        tic.setComision(Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 128, 8)));
                        tic.setfPago(getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 20, 1));
                        tic.setcLineaAerea(getLineaString(getCharsLinea(getIndexLinea("M3")), 59, 2));
                        String contenidoLineaFormaPago = getLineaString(getCharsLinea(lineaFormaPago), 1, 2);
                        
                        //Tasas
                        String signo1 = getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 46, 1);
                        double tasa1 =  parseoSeguro(getLineaString(getCharsLinea(getIndexLinea("M2")), 47, 7));
                        String signo2 = getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 56, 1);
                        double tasa2 =  parseoSeguro(getLineaString(getCharsLinea(getIndexLinea("M2")), 57, 7));
                        String signo3 = getLineaString(getCharsLinea(getIndexLinea(identificadorDelTicket)), 66, 1);
                        double tasa3 =  parseoSeguro(getLineaString(getCharsLinea(getIndexLinea("M2")), 67, 7));
                        double tasa_final = getTasaFinal(signo1, tasa1, signo2, tasa2, signo3, tasa3);
                        
                        //Moneda y valores 
                        this.setMoneda(getLineaString(getCharsLinea(getIndexLinea("M2")), 35, 3));
                        double neto = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("M2")), 38, 8));
                        double netoCLP = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("M2")), 80, 8));
                        String moneda_neto = getLineaString(getCharsLinea(getIndexLinea("M2")), 77, 3);
                        
                        if(getMoneda().equals("CLP")){
                            int int_neto = (int) neto;
                            switch(moneda_neto){
                                case "CLP":
                                    int int_vt = (int) tasa_final;
                                    this.setValorNeto(int_neto);
                                    this.setValorTasas(int_vt);
                                    this.setValorFinal(getValorNeto()-getValorTasas());
                                    break;
                                case "USD":
                                    this.setValorNeto(neto);
                                    this.setValorTasas(getValorTasas()*getValorTipoDeCambio());
                                    this.setValorFinal(getValorNeto()-getValorTasas());
                                    break;
                            }

                        }

                        if(getMoneda().equals("USD")){
                            switch (moneda_neto) {
                                case "USD":
                                    this.setValorNeto(neto);
                                    this.setValorTasas(tasa_final);
                                    this.setValorFinal(getValorNeto()-getValorTasas());
                                    break;
                                case "CLP":
                                    this.setValorNeto(neto);
                                    this.setValorTipoDeCambio(netoCLP/getValorNeto());
                                    this.setValorTasas(tasa_final/getValorTipoDeCambio());
                                    this.setValorFinal(getValorNeto()-getValorTasas());
                                    break;
                                }
                        }
                        //Investigar las siglas de las otras formas de pago
                        if (contenidoLineaFormaPago.equals("CA")) {
                            tic.setfPago(contenidoLineaFormaPago);
                        }
                        this.pajaseros.add(tic); 
                        //test
                    }
                }    
            }
            
        //Recorremos los segmentos
           if (CANTIDAD_SEGMENTOS > 0) {
               for (int j = 1; j <= CANTIDAD_SEGMENTOS; j++) {
                   String identificador = "M3"+0+j;
                   final int index_linea = getIndexLinea(identificador);
                   if(index_linea != 0){
                        Segmento seg;
                        seg = new Segmento();
                        seg.setFechaSalida(getLineaString(getCharsLinea(index_linea), 10, 5));
                        seg.setCodSalida(getLineaString(getCharsLinea(index_linea), 19, 3));
                        seg.setNomSalida(getLineaString(getCharsLinea(index_linea), 22, 17));
                        seg.setHorSalida(getLineaString(getCharsLinea(index_linea), 68, 5));             
                        seg.setCodClase(getLineaString(getCharsLinea(index_linea), 66, 2));

                        final int dias_de_viaje = Integer.parseInt(getLineaString(getCharsLinea(index_linea), 91, 1));

                        if(dias_de_viaje != 0){
                            seg.setFechaLlegada(sumarDias(seg.getFechaSalida() ,dias_de_viaje));
                        }else{
                            seg.setFechaLlegada(seg.getFechaSalida());
                        }
                        seg.setCodLlegada(getLineaString(getCharsLinea(index_linea), 39, 3));
                        seg.setNomLlegada(getLineaString(getCharsLinea(index_linea), 42, 17));
                        seg.setHorLLegada(getLineaString(getCharsLinea(index_linea), 73, 5));
                        seg.setNumeroVuelo(getLineaString(getCharsLinea(index_linea), 61, 8));
                        seg.setNumeroSegmento(Integer.parseInt(getLineaString(getCharsLinea(index_linea), 3, 2)));
                        seg.setLineaAerea(getLineaString(getCharsLinea(getIndexLinea(identificador)), 59, 2));
                        this.segmentos.add(seg);
                        this.getRuta();
                   }else{
                       CANTIDAD_SEGMENTOS++;
                   }
                }
            }
        }         
        

        if(esEMD && LINEA_EMD_DATOS > 0 && CANTIDAD_PERSONAS > 0){
                
            //Recorremos los tickets
            for (int i = 1; i <= CANTIDAD_PERSONAS; i++) {
                Ticket tic = new Ticket();
                String identificador_datos_personas = "M1"+0+i;
                int posicionLineaMG = getIndexLinea("MG");
                int lineaTasas = (posicionLineaMG+3);
                this.fechaRemision = getFechaEmision();
                this.setNumeroPnr(getLineaString(getCharsLinea(0), 54, 8));
                this.setValorTasas(Double.parseDouble(getLineaString(getCharsLinea(lineaTasas), 3, 21)));
                tic.setNombrePasajero(getLineaString(getCharsLinea(getIndexLinea(identificador_datos_personas)), 9, 64));
                String ticket = getLineaString(getCharsLinea(LINEA_EMD_DATOS), 44, 10).trim();
                if(!ticket.equals("")){
                    tic.setTicket(ticket);
                }
                tic.setCodEmd(getLineaString(getCharsLinea(LINEA_EMD_DATOS), 26, 14));
                double valorTotal = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 92, 18));
                tic.setValorEmd(valorTotal);
                tic.setfPago(getLineaString(getCharsLinea(getIndexLinea("MG")), 167, 2));
                tic.setcLineaAerea(getLineaString(getCharsLinea(LINEA_EMD_DATOS), 26, 3));
                tic.setTipoPasajero(getLineaString(getCharsLinea(LINEA_EMD_DATOS), 5, 3));
                tic.setComision(Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 128, 18)));
                this.pajaseros.add(tic); 
            }  
            
            //Recorremos los segmentos
            if (CANTIDAD_SEGMENTOS > 0) {
                for (int j = 1; j <= CANTIDAD_SEGMENTOS; j++) {
                    String identificador = "M3"+0+j;
                    final int index_linea = getIndexLinea(identificador);
                    if(index_linea != 0){
                        Segmento seg;
                        seg = new Segmento();
                        seg.setFechaSalida(getLineaString(getCharsLinea(index_linea), 10, 5));
                        seg.setCodSalida(getLineaString(getCharsLinea(index_linea), 19, 3));
                        seg.setNomSalida(getLineaString(getCharsLinea(index_linea), 22, 17));
                        seg.setHorSalida(getLineaString(getCharsLinea(index_linea), 68, 5));

                        final int dias_de_viaje = Integer.parseInt(getLineaString(getCharsLinea(index_linea), 91, 1));

                        if(dias_de_viaje != 0){
                            seg.setFechaLlegada(sumarDias(seg.getFechaSalida() ,dias_de_viaje));
                        }else{
                            seg.setFechaLlegada(seg.getFechaSalida());
                        }

                        seg.setCodLlegada(getLineaString(getCharsLinea(index_linea), 39, 3));
                        seg.setNomLlegada(getLineaString(getCharsLinea(index_linea), 42, 17));
                        seg.setHorLLegada(getLineaString(getCharsLinea(index_linea), 73, 5));
                        seg.setNumeroVuelo(getLineaString(getCharsLinea(index_linea), 61, 8));
                        seg.setNumeroSegmento(Integer.parseInt(getLineaString(getCharsLinea(index_linea), 3, 2)));
                        seg.setLineaAerea(getLineaString(getCharsLinea(getIndexLinea(identificador)), 59, 2));
                        this.segmentos.add(seg);
                        this.getRuta();
                    }else{
                        j++;
                    }   
                }
            }
        }
    }
    
    private double parseoSeguro(String variable){
        
        String trim = variable.trim();
        if(!trim.equals("")){
            double var;
            var = Double.parseDouble(trim);
            return var;
        }
        
        return 0;
    }
    
    private double getTasaFinal(String signo1, double tasa1, String signo2, double tasa2, String signo3, double tasa3){
        
        String[] arrayTax = new String[3];
        double sum = 0;

        if(tasa1>0){
            arrayTax[0] = (signo1+tasa1);
        }if(tasa2>0){
            arrayTax[1] = (signo2+tasa2);
        }if(tasa3>0){
            arrayTax[2] = (signo3+tasa3);
        }
        
        for (String cadena : arrayTax) {
            if(cadena != null){
                double cadena_f = Double.parseDouble(cadena);
                sum += cadena_f;
            }
        }

        return sum;                  
    }
    
    private String sumarDias(String fecha_salida, int dias_a_sumar) {
       
        String fecha_f = fecha_salida.substring(0, 2);
        int value1 = Integer.parseInt(fecha_f);
        int suma = (dias_a_sumar+value1);
        
        return suma+fecha_salida.substring(2, fecha_salida.length());
    }
    
    private int getIndexLinea(String texto){
        
        int index = 0;        
        
        for (int i = 0; i < bf.size(); i++) {
            if (bf.get(i).startsWith(texto)) {
               index = i;
            }
        }
        
        return index;
        
    }
    
  
    
    private int getIncidencias(String comienzoLinea){

        int cont = 0;
        
        for (String linea : bf) {
            if(linea.startsWith(comienzoLinea)){
                cont++;
            }
        }
        return cont;
        
    }
     
    private String getLineaString(char[] array, int inicio, int largo) {

        final int inicio_f = (inicio-1);
        
        StringBuilder sb;
        sb = new StringBuilder();
        sb.append(array, inicio_f, largo);
        String cadena_final = sb.toString();

        return cadena_final;

    }

    private char[] getCharsLinea(int indexLinea) {

        String lineaSeleccionada;
        lineaSeleccionada = bf.get(indexLinea);
        char[] array;
        array = lineaSeleccionada.toCharArray();

        return array;
    }

    private String getNumFile() throws FileNotFoundException, IOException {
        String incidencia = Parametros.getInstance().getSegmento_numfile();
        int largo = incidencia.length();
        for (String cadena : bf) {
            if (!cadena.equals("") && cadena.length() > largo) {
                if (cadena.substring(0, largo).equals(incidencia)) {
                    String cadena_final = cadena.substring(largo).trim();
                    String[] split = cadena_final.split(" ");
                    System.out.println("num_file : " + split[0]);
                    return split[0];
                }
            }
        }
        return "";
    }
    
    @Override
    public String toString() {
        return "Archivo{" + "archivo=" + archivo + ", numeroPnr=" + numeroPnr + ", fechaEmision=" + fechaEmision + ", moneda=" + moneda + ", valor_neto=" + valorNeto + ", valor_final=" + valorFinal + ", valor_tasas=" + valorTasas + ", numero_file=" + numeroFile + ", fecha_anulacion=" + fechaAnulacion + ", fecha_remision=" + fechaRemision + ", ruta=" + ruta + ", tipo=" + tipo + ", estado=" + estado + ", pajaseros=" + pajaseros + ", segmentos=" + segmentos + '}';
    }

    public double getValorTipoDeCambio() {
        return valorTipoDeCambio;
    }

    public void setValorTipoDeCambio(double valorTipoDeCambio) {
        this.valorTipoDeCambio = valorTipoDeCambio;
    }
    
    public File getArchivo() {
        return archivo;
    }

    public void setArchivo(File archivo) {
        this.archivo = archivo;
    }

    public ArrayList<Ticket> getPajaseros() {
        return pajaseros;
    }

    public void setPajaseros(ArrayList<Ticket> pajaseros) {
        this.pajaseros = pajaseros;
    }

    public ArrayList<Segmento> getSegmentos() {
        return segmentos;
    }

    public void setSegmentos(ArrayList<Segmento> segmentos) {
        this.segmentos = segmentos;
    }

    public String getNumeroPnr() {
        return numeroPnr;
    }

    public String getNumeroFile() {
        return numeroFile;
    }

    public void setNumeroFile(String numeroFile) {
        this.numeroFile = numeroFile;
    }

    public String getRuta() {
        ruta = "";
        if (this.segmentos.size() > 0) {

            for (Segmento seg : this.segmentos) {
                ruta += seg.getCodSalida() + "/";
            }
            ruta += this.segmentos.get(segmentos.size() - 1).getCodLlegada();
        }
        return ruta;
    }
    
    public String getFechaRemision() {
        return fechaRemision;
    }

    public void setFechaRemision(String fechaRemision) {
        this.fechaRemision = fechaRemision;
    }
    
    public void setFechaEmision(String fechaEmision) {
        this.fechaEmision = fechaEmision;
    }
    
    public String getFechaEmision() {
        
        return this.fechaEmision;
    }
    
    public String getFechaAnulacion() {
        return fechaAnulacion;
    }
    

    public String getMoneda() {
        return moneda;
    }

    public void setNumeroPnr(String numeroPnr) {
        this.numeroPnr = numeroPnr.trim();
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public double getValorNeto() {
        return valorNeto;
    }

    public void setValorNeto(double valorNeto) {
        this.valorNeto = valorNeto;
    }

    public double getValorFinal() {
        return valorFinal;
    }

    public void setValorFinal(double valorFinal) {
        this.valorFinal = valorFinal;
    }

    public double getValorTasas() {
        return valorTasas;
    }

    public void setValorTasas(double valorTasas) {
        this.valorTasas = valorTasas;
    }

    private enum Meses {
        JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV, DEC;
    }
    

    private String getFechaSQL(String fecha){
        
        String mes = fecha.substring(2 , fecha.length());
        String dia = fecha.substring(0 , 2);
        Meses meses = Meses.valueOf(mes);
        switch(meses){
            case JAN : fecha = "01";
                break;
            case FEB : fecha = "02";
                break;
            case MAR : fecha = "03";
                break;
            case APR : fecha = "04";
                break;
            case MAY : fecha = "05";
                break;
            case JUN : fecha = "06";
                break;
            case JUL : fecha = "07";
                break;
            case AUG : fecha = "08";
                break;
            case SEP : fecha = "09";
                break;
            case OCT : fecha = "10";
                break;
            case NOV : fecha = "11";
                break;
            case DEC : fecha = "12";
                break;
        }
        
        if (fecha.length() == 2) {
            
            //Aaño actual
            Calendar now = Calendar.getInstance();
            int año = now.get(Calendar.YEAR);
            return dia+"-"+fecha+"-"+año;
        }
        
        return "";
    }
    
    public String getTipo() {
        if (tipo.trim().equals("")) {
            return "TKT";
        }
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public static void main(String[] args) throws FileNotFoundException, IOException{
        try {
        Archivo lc;
        lc = new Archivo(new File("C:\\Users\\Felipe\\Desktop\\pruebas\\lectura\\GEQPSM00.PNR"));
        System.out.println(lc);
            ArchivoDAO a = new ArchivoDAO();
            try {
                a.insertArchivo(lc);
            } catch (SQLException ex) {
                Logger.getLogger(Archivo.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Archivo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Archivo.class.getName()).log(Level.SEVERE, null, ex);
        }  
    }
}


