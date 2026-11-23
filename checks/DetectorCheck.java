package dev.lotguard.app;
import java.util.List;
public class DetectorCheck {
    static final String MENU="Manage Accounts\nSettings\n123456 - Demo-Server\nMessages";
    static final String TICKET="Market Execution\nEURUSD\nVolume ";
    static Detector detector(){return new Detector(List.of(new Detector.Profile("123456","Demo-Server","EURUSD","0.05"),
        new Detector.Profile("987654","Demo-Server","EURUSD","0.50")));}
    static Detector.Result scan(Detector d,String s){d.scan(s);return d.scan(s);}
    static void check(boolean b,String label){if(!b)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args){
        Detector d=detector();
        check(scan(d,TICKET+"0.50").warning()==null,"no account");
        scan(d,MENU);
        check(scan(d,TICKET+"0.05").warning()==null,"matching preset");
        check(scan(d,TICKET+"0.50").warning()!=null,"oversize");
        check(scan(d,TICKET+"0.50").warning()==null,"deduplication");
        scan(d,"Accounts");
        check(scan(d,TICKET+"1.00").warning()==null,"old account cleared");
        scan(d,MENU.replace("123456","987654"));
        check(scan(d,TICKET+"0.50").state().contains("within saved preset"),"second account preset");
        check(scan(d,TICKET+"0.90\nVolume 0.05").state().contains("unreadable"),"conflicting decimal values");
        check(scan(d,TICKET.replace("EURUSD","USDJPY")+"1.50").warning()==null,"symbol mismatch");
        scan(d,MENU);
        check(scan(d,"New Order\nEURUSD\n-0.1 -0.01 0.50 +0.01 +0.1").warning()!=null,"classic volume stepper");
        scan(d,"Accounts");
        scan(d,MENU.replace("Demo-Server","Other-Server"));
        check(scan(d,TICKET+"1.00").warning()==null,"unknown server");
    }
}
