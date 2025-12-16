package pl.zapala.projekt;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;


@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
@Theme(variant = Lumo.DARK)
public class AppShell implements AppShellConfigurator {

}
