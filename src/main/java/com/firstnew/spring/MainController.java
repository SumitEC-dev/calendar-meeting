package com.firstnew.spring;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
@Controller
public class MainController {
  
    private static final String APPLICATION_NAME = "";
	private static NetHttpTransport httpTransport;
	private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static com.google.api.services.calendar.Calendar client;
	
	private static SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
	
	GoogleClientSecrets clientSecrets;
	GoogleAuthorizationCodeFlow flow;
	Credential credential;

	@Value("${google.client.client-id}")
	private String clientId;
	@Value("${google.client.client-secret}")
	private String clientSecret;
	@Value("${google.client.redirectUri}")
	private String redirectURI;
	@Value("${google.client.redirectUri.available.slot}")
	private String redirectURIAvailableSlot;
	
	private Set<Event> events = new HashSet<>();
	
	private final int START_HOUR = 8;
	private final int START_MIN = 00;
	private final int END_HOUR = 20;
	private final int END_MIN = 00;
	
	private static boolean isAuthorised = false;

	public void setEvents(Set<Event> events) {
		this.events = events;
	}
  
    private String authorize(String redirectURL) throws Exception {
    	AuthorizationCodeRequestUrl authorizationUrl;
		if (flow == null) {
			Details web = new Details();
			web.setClientId(clientId);
			web.setClientSecret(clientSecret);
			clientSecrets = new GoogleClientSecrets().setWeb(web);
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
					Collections.singleton(CalendarScopes.CALENDAR)).build();
		}
		authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURL);
		
		isAuthorised = true;
		
		return authorizationUrl.build();
	}
    
    @RequestMapping(value = "/calendar", method = RequestMethod.GET)
	public RedirectView googleConnectionStatus(HttpServletRequest request) throws Exception {
    	return new RedirectView(authorize(redirectURI));
	}
    
    @RequestMapping(value = "/calendar", method = RequestMethod.GET, params = "code")
    public String oauth2Callback(@RequestParam(value = "code") String code, Model model) {
    	if(isAuthorised) {
    		try {
    			
    			model.addAttribute("title", "Calendar Event Created Sucessfully");
		        model.addAttribute("calendarObjs", getTodaysCalendarEventList(code, redirectURI));
		        
			} catch (Exception e) {
				model.addAttribute("calendarObjs", new ArrayList<CalendarObj>());
			}
			
    		return "agenda";
    	} else {
    		return "/";
    	}	
	}
    
    @RequestMapping(value = "/slot/available", method = RequestMethod.GET)
	public RedirectView checkConnectionStatus(HttpServletRequest request, Model model) throws Exception {
    	return new RedirectView(authorize(redirectURIAvailableSlot));
	}
    
    @RequestMapping(value = "/slot/available", method = RequestMethod.GET, params = "code")
    public String oauth2Callback2(@RequestParam(value = "code") String code, Model model) {
    	if(isAuthorised) {
    		try {
				
    			List<CalendarObj> calendarEventList = getTodaysCalendarEventList(code, redirectURIAvailableSlot);
    			List<CalendarObj> freeCalendarObjs = populateAvailableSlot(calendarEventList);
    			
				model.addAttribute("title", "Today's Available Slots (" +START_HOUR+":"+START_MIN +" - "+END_HOUR+":"+END_MIN +")");
		        model.addAttribute("calendarObjs", freeCalendarObjs);
		        
			} catch (Exception e) {
				model.addAttribute("title", e.getMessage());
				model.addAttribute("calendarObjs", new ArrayList<CalendarObj>());
			}
    		
    		return "availableslots";
    	} else {
    		return "/";
    	}	
	}
    
    public Set<Event> getEvents() throws IOException {
		return this.events;
	}
  
    @RequestMapping(value = "/error", method = RequestMethod.GET)
    public String accessDenied(Model model) {
  
    	model.addAttribute("message", "Not authorised.");	
        return "login";
        
    }
  
    @RequestMapping(value = { "/", "/login", "/logout" }, method = RequestMethod.GET)
    public String login(Model model) {
    	isAuthorised = false;
    	
    	return "login";
    }   
    
    
    @SuppressWarnings("deprecation")
	private List<CalendarObj> getTodaysCalendarEventList(String calenderApiCode, String redirectURL) {
		try {
			com.google.api.services.calendar.model.Events eventList;
			

			LocalDateTime localDateTime = LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault()); 
			LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN); 
			LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);

			DateTime date1 = new DateTime(Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant()));
			DateTime date2 = new DateTime(Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant()));

			TokenResponse response = flow.newTokenRequest(calenderApiCode).setRedirectUri(redirectURL).execute();
			credential = flow.createAndStoreCredential(response, "userID");
			client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
			Events events = client.events();
			
			 Event newEvent = new Event()
	                    .setSummary("Testing Event")
	                    .setLocation("Bangalore")
	                    .setDescription("This is a sample event created by the Calendar API.");
			 String meetLink = generateHangoutsMeetLink(newEvent);
	            DateTime startDateTime1 = new DateTime("2024-05-22T11:00:00-07:00");
	            EventDateTime start = new EventDateTime()
	                    .setDateTime(startDateTime1)
	                    .setTimeZone("America/Los_Angeles");
	            newEvent.setStart(start);

	            DateTime endDateTime1 = new DateTime("2024-05-22T11:30:00-07:00");
	            EventDateTime end = new EventDateTime()
	                    .setDateTime(endDateTime1)
	                    .setTimeZone("America/Los_Angeles");
	            newEvent.setEnd(end);
	            
	            EventAttendee[] attendees = {
	                    new EventAttendee().setEmail("jaideepkm@gmail.com"),
	                    new EventAttendee().setEmail("sanushome7@gmail.com"),
	                    new EventAttendee().setEmail("jaideepkm@o.com")
	            };
	            newEvent.setAttendees(Arrays.asList(attendees));
	            
	            

	            // Send notifications to attendees


	            String calendarId = "primary";
	            newEvent = client.events().insert(calendarId, newEvent).execute();
	            System.out.printf("Event created: %s\n", newEvent.getHtmlLink());
	            
	          
	            
			eventList = events.list("primary").setSingleEvents(true).setTimeMin(date1).setTimeMax(date2).setOrderBy("startTime").execute();

			List<Event> items = eventList.getItems();

			CalendarObj calendarObj;
			List<CalendarObj> calendarObjs = new ArrayList<CalendarObj>();

			for (Event event : items) {

				Date startDateTime = new Date(event.getStart().getDateTime().getValue());
				Date endDateTime = new Date(event.getEnd().getDateTime().getValue());
	
	
				long diffInMillies = endDateTime.getTime() - startDateTime.getTime();
				int diffmin = (int) (diffInMillies / (60 * 1000));
	
				calendarObj = new CalendarObj();
	
				if(event.getSummary() != null && event.getSummary().length() > 0) {
					calendarObj.setTitle(event.getSummary());
				} else {
					calendarObj.setTitle("No Title");
				}

				calendarObj.setStartHour(startDateTime.getHours());
				calendarObj.setStartMin(startDateTime.getMinutes());
				calendarObj.setEndHour(endDateTime.getHours());
				calendarObj.setEndMin(endDateTime.getMinutes());
				calendarObj.setDuration(diffmin);
	
				calendarObj.setStartEnd(sdf.format(startDateTime) + " - " + sdf.format(endDateTime));            
		            
				calendarObjs.add(calendarObj);
			}

			return calendarObjs;
	        
		} catch (Exception e) {
			return new ArrayList<CalendarObj>();
		}
	}
    
    private String generateHangoutsMeetLink(Event event) {
        return "https://meet.google.com/xyz-abc-123";
    }
    
    private void sendEmailNotification(String recipientEmail, String subject, String bodyText) {
        try {
            MimeMessage email = createEmail(recipientEmail, "me", subject, bodyText);
            Gmail gmailClient = null;
			sendMessage(gmailClient, "me", email);
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }
    }

    private MimeMessage createEmail(String to, String from, String subject, String bodyText) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    private void sendMessage(Gmail service, String userId, MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(rawMessageBytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        service.users().messages().send(userId, message).execute();
    }
    
    private List<CalendarObj> populateAvailableSlot(List<CalendarObj> calendarEventList) {
    	int freeSlotStartHour = START_HOUR;
		int freeSlotStartMin = START_MIN;
		int freeSlotEndHour = END_HOUR;
		int freeSlotEndMin = END_MIN;
		
		CalendarObj freeCalEvent;
		CalendarObj thisCalEvent;
		CalendarObj nextCalEvent; 
		List<CalendarObj> freeCalendarObjs = new ArrayList<CalendarObj>();
		
		for (int count = 0; count < calendarEventList.size() - 1; count++) {
			thisCalEvent = calendarEventList.get(count);
			
			int thisEventStartHour  = thisCalEvent.getStartHour();
			int thisEventStartMin  = thisCalEvent.getStartMin();
			int thisEventEndHour  = thisCalEvent.getEndHour();
			int thisEventEndMin  = thisCalEvent.getEndMin();
			
			if(count == 0) { //Check time slot of last schedule
				if((thisEventStartHour > START_HOUR) || (thisEventStartHour == START_HOUR && thisEventStartMin > START_MIN)) {
					freeSlotStartHour = START_HOUR;
					freeSlotStartMin = START_MIN;
					
					freeSlotEndHour = thisEventStartHour;
					freeSlotEndMin = thisEventStartMin;
					
					int diffmin = (thisEventStartHour - START_HOUR) * 60 + Math.abs(thisEventStartMin - START_MIN);
					
					freeCalEvent = new CalendarObj();
					        					  
					freeCalEvent.setTitle("Free Slot 1");
					freeCalEvent.setStartHour(freeSlotStartHour);
					freeCalEvent.setStartMin(freeSlotStartMin);
					freeCalEvent.setEndHour(freeSlotEndHour);
					freeCalEvent.setEndMin(freeSlotEndMin);
					freeCalEvent.setDuration(diffmin);
					
					freeCalEvent.setStartEnd(freeSlotStartHour+":"+freeSlotStartMin+ " - " + freeSlotEndHour+":"+freeSlotEndMin);
					
					freeCalendarObjs.add(freeCalEvent);
				} 
			} 
			
			nextCalEvent = calendarEventList.get(count + 1);
			
			int nextEventStartHour  = nextCalEvent.getStartHour();
			int nextEventStartMin  = nextCalEvent.getStartMin();
			
			freeSlotStartHour = thisEventEndHour;
			freeSlotStartMin = thisEventEndMin;
			
			freeSlotEndHour = nextEventStartHour;
			freeSlotEndMin = nextEventStartMin;
			
			int diffmin = (nextEventStartHour - thisEventEndHour - 1) * 60 + Math.abs(nextEventStartMin + thisEventEndMin);
			if(nextEventStartMin == thisEventEndMin) {
				diffmin = (nextEventStartHour - thisEventEndHour) * 60;
			}
			
			if(diffmin > 0) {
				freeCalEvent = new CalendarObj();
				freeCalEvent.setTitle("Free Slot " + (count + 1));
				freeCalEvent.setStartHour(freeSlotStartHour);
				freeCalEvent.setStartMin(freeSlotStartMin);
				freeCalEvent.setEndHour(freeSlotEndHour);
				freeCalEvent.setEndMin(freeSlotEndMin);
				freeCalEvent.setDuration(diffmin);
				
				freeCalEvent.setStartEnd(freeSlotStartHour+":"+freeSlotStartMin+ " - " + freeSlotEndHour+":"+freeSlotEndMin);
				
				freeCalendarObjs.add(freeCalEvent);
			}
		}
		
		
		//Check time slot of last schedule 
		thisCalEvent = calendarEventList.get(calendarEventList.size() - 1);
		
		int thisEventEndHour  = thisCalEvent.getEndHour();
		int thisEventEndMin  = thisCalEvent.getEndMin();
		
		if((thisEventEndHour < END_HOUR) || (thisEventEndHour == END_HOUR && thisEventEndMin < END_MIN)) {
			freeSlotStartHour = END_HOUR;
			freeSlotStartMin = END_MIN;
			
			freeSlotEndHour = thisEventEndHour;
			freeSlotEndMin = thisEventEndMin;
			
			int diffmin = (END_HOUR - thisEventEndHour - 1) * 60 + Math.abs(thisEventEndMin - END_MIN);
			
			freeCalEvent = new CalendarObj();
			        					  
			freeCalEvent.setTitle("Free Slot " + (calendarEventList.size()));
			freeCalEvent.setStartHour(freeSlotStartHour);
			freeCalEvent.setStartMin(freeSlotStartMin);
			freeCalEvent.setEndHour(freeSlotEndHour);
			freeCalEvent.setEndMin(freeSlotEndMin);
			freeCalEvent.setDuration(diffmin);
			
			freeCalEvent.setStartEnd(freeSlotEndHour+":"+freeSlotEndMin +" - " +freeSlotStartHour+":"+freeSlotStartMin);
			
			freeCalendarObjs.add(freeCalEvent);
		}
		
		return freeCalendarObjs;
    }
    
}