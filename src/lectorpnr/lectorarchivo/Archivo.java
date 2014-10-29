package lectorpnr.lectorarchivo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import lectorpnr.datos.db.ArchivoDAO;
import lectorpnr.datos.db.Parametros;

/**
 *
 * @author Felipe
 */
public class Archivo {

    private File archivo;
    private String numeroPnr;
    private String fechaEmision;
    private String moneda;
    private double valor_neto;
    private double valor_final;
    private double valor_tasas; //tasas embarque tax
    private String numero_file;//numero de negocio se extrae desde parametros.conf
    private String fecha_anulacion;
    private String fecha_remision;
    private String ruta;
    private String tipo;
    private String estado;
    private ArrayList<Ticket> pajaseros;
    private ArrayList<Segmento> segmentos;
    private final ArrayList<String> bf;
    //CONSTANTES
    private final String AÑO_EMISION = getLineaString(getCharsLinea(getIndexLinea("M3")), 236, 4);
    private final String FECHA_EMISION = getLineaString(getCharsLinea(0), 117, 5);

    public Archivo(File archivo) throws FileNotFoundException, IOException {
        this.archivo = archivo;
        this.numeroPnr = "";
        this.fechaEmision = "";
        this.moneda = "";
        this.valor_neto = 0.0;
        this.valor_final = 0.0;
        this.valor_tasas = 0.0;
        this.numero_file = "";
        this.fecha_anulacion = "";
        this.fecha_remision = "";
        this.ruta = "";
        this.tipo = "TKT";
        this.estado = "";
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
        
        //LINEA CONSTANTE QUE INDICA EL TIPO DE TRANSACCION 
        String verificacion = getLineaString(getCharsLinea(0), 14, 1);
        
        //1 = SOLO TICKET
        //A = SOLO EMD
        //B = EMD ASOCIADO CON UN TICKET
        
        //IGNORAMOS LOS TICKETS Y EMD VACIOS
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
        
        //FORMATO YYYY-MMM-DD (USUARIO SQL EN ESPAÑOL)
        this.setFechaEmision(getFechaSQL(FECHA_EMISION, AÑO_EMISION));
        this.setNumeroPnr(getLineaString(getCharsLinea(0), 54, 8));
        
        //1 = SOLO TICKET
        //A = SOLO EMD
        //B = EMD ASOCIADO CON UN TICKET        
        final boolean esTicket = getLineaString(getCharsLinea(0), 14, 1).equals("1");
        final boolean esEMD = getLineaString(getCharsLinea(0), 14, 1).equals("A");
        final boolean esAsociado = getLineaString(getCharsLinea(0), 14, 1).equals("B"); 

        //CANTIDAD DE INCIDENCIAS
        final int CANTIDAD_PERSONAS = getIncidencias("M1");
        final int CANTIDAD_SEGMENTOS = getIncidencias("M3"); 
        //DATOS DEL EMD EN LA LINEA MG
        final int EMD_DATOS = getIndexLinea("MG");
        
        //SI ES UN TICKET...
        if(esTicket){
            
            //M2 = LINEA CON DATOS DE NEGOCIO (SOLO APARECE)
            double valor_total = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("M2")), 80, 8));
            double monto_usd = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("M2")), 38, 8));
            double valor_usd = (valor_total/monto_usd);
            double redoneado_total = Math.round(valor_usd*100.0) / 100.0;
            this.setValor_final(redoneado_total);  
            double valor_neto_ = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("M2")), 146, 8));
            double resultado_neto = (valor_neto_/valor_usd);
            double redondeado_neto = Math.round(resultado_neto * 100.0) / 100.0;
            this.setValor_neto(redondeado_neto);
            this.setMoneda(getLineaString(getCharsLinea(getIndexLinea("M2")), 35, 3));
            
            //RECORREMOS LOS SEGMENTOS
            if (CANTIDAD_SEGMENTOS > 0) {
                for (int j = 1; j <= CANTIDAD_SEGMENTOS; j++) {
                    String identificador = "M3"+0+j;
                    final int index_linea = getIndexLinea(identificador);
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
                }

            //RECORREMOS LOS TICKETS...
            if(CANTIDAD_PERSONAS > 0){
                for (int i = 1; i <= CANTIDAD_PERSONAS; i++) {
                    if(esTicket){
                        String identificador_ticket = "M2"+0+i;
                        String identificador_datos_personas = "M1"+0+i;
                        Ticket tic = new Ticket();
                        tic.setNombrePasajero(getLineaString(getCharsLinea(getIndexLinea(identificador_datos_personas)), 5, 64));
                        tic.setTicket(getLineaString(getCharsLinea(getIndexLinea(identificador_ticket)), 234, 10));
                        tic.setTipoPasajero(getLineaString(getCharsLinea(getIndexLinea(identificador_ticket)), 5, 3));
                        tic.setfPago(getLineaString(getCharsLinea(getIndexLinea(identificador_ticket)), 20, 1));
                        tic.setComision(Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea(identificador_ticket)), 128, 8)));
                        tic.setfPago(getLineaString(getCharsLinea(getIndexLinea(identificador_ticket)), 20, 1));
                        tic.setcLineaAerea(getLineaString(getCharsLinea(getIndexLinea("M3")), 59, 2));
                        this.pajaseros.add(tic);   
                    }
                }
            }
        }
    }
}
            
        
//        
//        if(esEMD){
//            
//            if(EMD_DATOS > 0){
//                double valor_total = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 92, 18));
//                this.setValor_final(valor_total);
////                double monto_usd = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 38, 8));
////                double valor_usd = (valor_total/monto_usd);
////                double redoneado_total = Math.round(valor_usd*100.0) / 100.0;
////                this.setValor_final(redoneado_total);  
////                double valor_neto_ = Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 146, 8));
////                double resultado_neto = (valor_neto_/valor_usd);
////                double redondeado_neto = Math.round(resultado_neto * 100.0) / 100.0;
////                this.setValor_neto(redondeado_neto);
////                this.setMoneda(getLineaString(getCharsLinea(getIndexLinea("MG")), 35, 3));
//            }
//
//    
//        
//        
//
//        
//                
//                                    
//                if (esEMD && EMD_DATOS > 0) {
//                    Ticket tic = new Ticket();
//                    String identificador_datos_personas = "M1"+0+i;
//                    this.fecha_remision = this.fechaEmision;
//                    this.setNumeroPnr(getLineaString(getCharsLinea(0), 54, 8));
//                    tic.setNombrePasajero(getLineaString(getCharsLinea(getIndexLinea(identificador_datos_personas)), 9, 64));
//                    tic.setTicket(getLineaString(getCharsLinea(EMD_DATOS), 44, 10));
//                    tic.setCodEmd(getLineaString(getCharsLinea(EMD_DATOS), 26, 14));
//                    tic.setcLineaAerea(getLineaString(getCharsLinea(EMD_DATOS), 26, 3));
//                    tic.setTipoPasajero(getLineaString(getCharsLinea(EMD_DATOS), 5, 3));
//                    tic.setComision(Double.parseDouble(getLineaString(getCharsLinea(getIndexLinea("MG")), 128, 18)));
//                    this.pajaseros.add(tic); 
//                }
//                
////            }
////        }
//    

          
            
            
            
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
        //System.out.println(incidencia);
        int largo = incidencia.length();
        for (String cadena : bf) {
            if (!cadena.equals("") && cadena.length() > largo) {
                //System.out.println(incidencia+" - "+cadena.substring(0,largo));
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
    
    public void tarifasTest(){
        
//        double tax_1 = Double.parseDouble(getLineaString(getCharsLinea(10), 46, 1));
//        double monto_tax_1 = Double.parseDouble(getLineaString(getCharsLinea(10), 47, 7));
//        double id_tax_1 = Double.parseDouble(getLineaString(getCharsLinea(10), 54, 2));
//        
//        double tax_2 = Double.parseDouble(getLineaString(getCharsLinea(10), 56, 1));
//        double monto_tax_2 = Double.parseDouble(getLineaString(getCharsLinea(10), 57, 7));
//        double id_tax_2 = Double.parseDouble(getLineaString(getCharsLinea(10), 64, 2));
//        
//        double tax_3 = Double.parseDouble(getLineaString(getCharsLinea(10), 66, 1));
//        double monto_tax_3 = Double.parseDouble(getLineaString(getCharsLinea(10), 67, 7));
//        double id_tax_3 = Double.parseDouble(getLineaString(getCharsLinea(10), 74, 2));
//        
//        double signo_tarifa_total = Double.parseDouble(getLineaString(getCharsLinea(10), 76, 1));
//        double tarifa_total = Double.parseDouble(getLineaString(getCharsLinea(10), 77, 11));
    }
    
    
    @Override
    public String toString() {
        return "Archivo{" + "archivo=" + archivo + ", numeroPnr=" + numeroPnr + ", fechaEmision=" + fechaEmision + ", moneda=" + moneda + ", valor_neto=" + valor_neto + ", valor_final=" + valor_final + ", valor_tasas=" + valor_tasas + ", numero_file=" + numero_file + ", fecha_anulacion=" + fecha_anulacion + ", fecha_remision=" + fecha_remision + ", ruta=" + ruta + ", tipo=" + tipo + ", estado=" + estado + ", pajaseros=" + pajaseros + ", segmentos=" + segmentos + '}';
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

    public String getNumero_file() {
        return numero_file;
    }

    public void setNumero_file(String numero_file) {
        this.numero_file = numero_file;
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
    
    public String getFecha_remision() {
        return fecha_remision;
    }

    public void setFecha_remision(String fecha_remision) {
        this.fecha_remision = fecha_remision;
    }
    
    public void setFechaEmision(String fechaEmision) {
        this.fechaEmision = fechaEmision;
    }
    
    public String getFechaEmision() {
        
        return this.fechaEmision;
    }
    
    public String getFecha_anulacion() {
        return fecha_anulacion;
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

    public double getValor_neto() {
        return valor_neto;
    }

    public void setValor_neto(double valor_neto) {
        this.valor_neto = valor_neto;
    }

    public double getValor_final() {
        return valor_final;
    }

    public void setValor_final(double valor_final) {
        this.valor_final = valor_final;
    }

    public double getValor_tasas() {
        return valor_tasas;
    }

    public void setValor_tasas(double valor_tasas) {
        this.valor_tasas = valor_tasas;
    }

    private enum Meses {
    JAN, FEB, MAR, APR, MAY, JUN, JUL, AUG, SEP, OCT, NOV, DEC;
    }
    
    //28DEC
    private String getFechaSQL(String fecha, String año){
        
        String mes = fecha.substring(2 , fecha.length());
        //DEC
        String dia = fecha.substring(0 , 2);
        //28
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
            //return año+"-"+mes+"-"+dia;
            return dia+"-"+fecha+"-"+año;
            //return mes+"-"+dia+"-"+año;
            //RETORNA 2013-07-11
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
        lc = new Archivo(new File("C:\\Users\\Felipe\\Desktop\\pruebas\\lectura\\BROPAV00.PNR"));
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


