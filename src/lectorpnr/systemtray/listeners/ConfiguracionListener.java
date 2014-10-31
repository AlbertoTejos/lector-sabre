
package lectorpnr.systemtray.listeners;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import lectorpnr.configurar.FrameConfiguracion;


public class ConfiguracionListener implements ActionListener{

    @Override
    public void actionPerformed(ActionEvent e) {
        FrameConfiguracion frame = new FrameConfiguracion();
    }
    
}
