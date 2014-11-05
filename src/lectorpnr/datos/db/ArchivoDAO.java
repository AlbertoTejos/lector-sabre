
package lectorpnr.datos.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import lectorpnr.lectorarchivo.Archivo;
import lectorpnr.lectorarchivo.Segmento;
import lectorpnr.lectorarchivo.Ticket;


public class ArchivoDAO extends Conexion {

    private final String campos_ticket = "pnr,num_file,ticket,old_ticket,cod_emd,fecha_emision,fecha_anula,fecha_remision,posicion_pasajero,nombre_pasajero,tipo_pasajero,cod_linea_aerea,ruta,moneda,valor_neto,valor_tasas,valor_final,comision,forma_pago,tipo,estado,gds";
    private final String campos_segmentos = "ticket,nro_segmento,cod_salida,cod_llegada,nom_salida,nom_llegada,fecha_salida,hora_salida,fecha_llegada,hora_llegada,nro_vuelo,linea_aerea,cod_clase";

    public ArchivoDAO() {
        super();
    }

    public void insertArchivo(Archivo arc) throws SQLException{

        Connection con = getConnection();
        if (arc.getEstado().equalsIgnoreCase("ANULADO")) {
            for (Ticket tic : arc.getPajaseros()) {
                con.createStatement().executeUpdate("UPDATE ticket SET estado ='" + arc.getEstado() + "' WHERE ticket = '" + tic.getTicket() + "'");
            }
            return;
        }

        con.createStatement().executeUpdate("BEGIN TRAN;");

        for (Ticket tic : arc.getPajaseros()) {
            
            
            con.createStatement().executeUpdate("DELETE FROM segmentos WHERE ticket='" + tic.getTicket() + "'");
            
            String numero_file = arc.getNumeroFile();
            if(numero_file.length() > 0 && !numero_file.equals("")){
                ResultSet resultado;
                resultado = con.createStatement().executeQuery("SELECT num_file as num_file FROM file_ where num_file = "+numero_file);
                //SI LA CONSULTA NO TRAE FILAS DESDE LA TABLA FILE, EL NUM_FILE NO SE INSERTA
                if(!resultado.next()) {
                    numero_file = "";
                }
            }


            if (tic.getTicket() != null && !tic.getTicket().equals("")) {
                con.createStatement().executeUpdate("DELETE FROM ticket WHERE ticket='" + tic.getTicket() + "'");
                con.createStatement().executeUpdate("INSERT INTO ticket(" + campos_ticket + ") VALUES('"
                        + arc.getNumeroPnr() + "','"
                        + numero_file + "','"
                        + tic.getTicket() + "','"
                        + tic.getOldTicket() + "','"
                        + tic.getCodEmd() + "','"
                        + arc.getFechaEmision() + "','"
                        + arc.getFechaAnulacion() + "','"
                        + arc.getFechaRemision() + "','"
                        + tic.getPosicion() + "','"
                        + tic.getNombrePasajero() + "','"
                        + tic.getTipoPasajero() + "','"
                        + tic.getcLineaAerea() + "','"
                        + arc.getRuta() + "','"
                        + arc.getMoneda() + "','"
                        + arc.getValorNeto() + "','"
                        + arc.getValorTasas() + "','"
                        + arc.getValorFinal() + "','"
                        + tic.getComision() + "','"
                        + tic.getfPago() + "','"
                        + arc.getTipo() + "','"
                        + "" + "','"
                        + 'S' 
                        + "'"
                        + ")");

            }

            if (!tic.getCodEmd().equals("")) {

                con.createStatement().executeUpdate("DELETE FROM ticket WHERE cod_emd='" + tic.getCodEmd() + "'");
                con.createStatement().executeUpdate("INSERT INTO ticket(" + campos_ticket + ") VALUES('"
                        + arc.getNumeroPnr() + "','"
                        + arc.getNumeroFile() + "','"
                        + tic.getTicket() + "','"
                        + "" + "','"
                        + tic.getCodEmd() + "','"
                        + arc.getFechaEmision()+ "','"
                        + "" + "','"
                        + arc.getFechaRemision() + "','"
                        + tic.getPosicion() + "','"
                        + tic.getNombrePasajero() + "','"
                        + tic.getTipoPasajero() + "','"
                        + tic.getcLineaAerea() + "','"
                        + arc.getRuta() + "','"
                        + arc.getMoneda() + "','"
                        + "0" + "','"
                        + arc.getValorTasas() + "','"
                        + tic.getValorEmd() + "','"
                        + tic.getComision() + "','"
                        + tic.getfPago() + "','"
                        + "EMD','"
                        + "" + "','"
                        + 'S'
                        + "'"
                        + ")");
            }

            for (Segmento seg : arc.getSegmentos()) {
                con.createStatement().executeUpdate("INSERT INTO segmentos(" + campos_segmentos + ") VALUES('"
                        + tic.getTicket() + "','"
                        + seg.getNumeroSegmento() + "','"
                        + seg.getCodSalida() + "','"
                        + seg.getCodLlegada() + "','"
                        + seg.getNomSalida() + "','"
                        + seg.getNomLlegada() + "','"
                        + seg.getFechaSalida() + "','"
                        + seg.getHorSalida() + "','"
                        + seg.getFechaLlegada() + "','"
                        + seg.getHorLLegada() + "','"
                        + seg.getNumeroVuelo() + "','"
                        + seg.getLineaAerea() + "','"
                        + seg.getCodClase() + "'"
                        + ")");
            }
        }

        con.createStatement().executeUpdate("COMMIT;");
    }

}

