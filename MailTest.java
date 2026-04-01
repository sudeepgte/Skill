import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class MailTest {
    public static void main(String[] args) {
        String to = "suchisuchithra184@gmail.com"; 
        String from = "suchisuchithra184@gmail.com";
        String host = "smtp.gmail.com";
        Properties properties = System.getProperties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.auth", "true");
        
        Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("suchisuchithra184@gmail.com", "ewum nftv pjkc kvbb");
            }
        });
        
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("Test Email");
            message.setText("This is a test.");
            Transport.send(message);
            System.out.println("Email Sent!");
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }
}
