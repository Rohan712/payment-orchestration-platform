package com.payment.notificationservice.service;

import com.payment.notificationservice.client.UserClient;
import com.payment.notificationservice.model.PaymentStatusEvent;
import com.payment.notificationservice.model.UserResponse;
import com.payment.notificationservice.notification.NotificationChannel;
import com.payment.notificationservice.notification.NotificationChannelFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;




@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {


    @Mock
    UserClient userClient;


    @Mock
    NotificationChannelFactory channelFactory;


    @Mock
    NotificationChannel emailChannel;


    private NotificationServiceImpl createService() {
// Constructor likely: NotificationServiceImpl(UserClient, NotificationChannelFactory)
        return new NotificationServiceImpl(userClient, channelFactory);
    }


    private static PaymentStatusEvent event(String userId, String txnId, Long amount, String currency, String status) {
        PaymentStatusEvent e = new PaymentStatusEvent();
        e.setUserId(userId);
        e.setTransactionId(txnId);
        e.setAmount(amount);
        e.setCurrency(currency);
        e.setStatus(status);
        return e;
    }


    private static UserResponse.UserData user(String id, String name, String email) {
        UserResponse.UserData u = new UserResponse.UserData();
        u.setUserId(id);
        u.setName(name);
        u.setEmail(email);
        u.setStatus("ACTIVE");
        return u;
    }


    @Test
    @DisplayName("TC-NOT-001 sendValidNotification")
    void sendValidNotification() throws Exception {
        NotificationServiceImpl service = createService();

        PaymentStatusEvent evt = event("user-001", "txn-001", 1999L, "INR", "SUCCESS");
        when(userClient.getUserById("user-001")).thenReturn(user("user-001", "Rohan", "rohan@example.com"));
        when(channelFactory.get("EMAIL")).thenReturn(emailChannel);

        assertDoesNotThrow(() -> service.notifyUser(evt));
        verify(emailChannel).send(eq("rohan@example.com"), anyString(), anyString(), anyMap());
    }
    @Test
    @DisplayName("TC-NOT-005 missingUserIdOrNullEvent")
    void missingUserIdOrNullEvent() {
        NotificationServiceImpl service = new NotificationServiceImpl(userClient, channelFactory);

        // Case 1: event == null
        assertDoesNotThrow(() -> service.notifyUser(null));
        // Ensure no interaction happens when event is null
        verifyNoInteractions(userClient, channelFactory, emailChannel);

        // Case 2: event.getUserId() == null
        PaymentStatusEvent evt = new PaymentStatusEvent();
        evt.setTransactionId("txn-null");
        evt.setStatus("FAILED");
        assertDoesNotThrow(() -> service.notifyUser(evt));

        // Still no downstream calls
        verifyNoInteractions(userClient, channelFactory, emailChannel);
    }
    @Test
    @DisplayName("TC-NOT-013 userNotFoundOrNoEmail")
    void userNotFoundOrNoEmail() {
        NotificationServiceImpl service = new NotificationServiceImpl(userClient, channelFactory);

        // Case 1: userClient returns null
        PaymentStatusEvent event = new PaymentStatusEvent();
        event.setUserId("user-null");
        event.setTransactionId("txn-013a");
        event.setStatus("FAILED");
        when(userClient.getUserById("user-null")).thenReturn(null);

        assertDoesNotThrow(() -> service.notifyUser(event));
        verify(userClient).getUserById("user-null");
        verifyNoInteractions(channelFactory, emailChannel);

        // Reset mocks for next case
        clearInvocations(userClient, channelFactory, emailChannel);

        // Case 2: user returned but email is null
        PaymentStatusEvent event2 = new PaymentStatusEvent();
        event2.setUserId("user-noemail");
        event2.setTransactionId("txn-013b");
        event2.setStatus("PENDING");

        var userWithoutEmail = new com.payment.notificationservice.model.UserResponse.UserData();
        userWithoutEmail.setUserId("user-noemail");
        userWithoutEmail.setName("Archit");
        userWithoutEmail.setEmail(null);

        when(userClient.getUserById("user-noemail")).thenReturn(userWithoutEmail);

        assertDoesNotThrow(() -> service.notifyUser(event2));
        verify(userClient).getUserById("user-noemail");
        verifyNoInteractions(channelFactory, emailChannel);

        // Reset mocks for final case
        clearInvocations(userClient, channelFactory, emailChannel);

        // Case 3: user returned but email is blank
        PaymentStatusEvent event3 = new PaymentStatusEvent();
        event3.setUserId("user-blank");
        event3.setTransactionId("txn-013c");
        event3.setStatus("SUCCESS");

        var userWithBlankEmail = new com.payment.notificationservice.model.UserResponse.UserData();
        userWithBlankEmail.setUserId("user-blank");
        userWithBlankEmail.setEmail("   "); // blank email

        when(userClient.getUserById("user-blank")).thenReturn(userWithBlankEmail);

        assertDoesNotThrow(() -> service.notifyUser(event3));
        verify(userClient).getUserById("user-blank");
        verifyNoInteractions(channelFactory, emailChannel);
    }
    @Test
    @DisplayName("TC-NOT-014 providerThrowsHandled")
    void providerThrowsHandled() throws Exception {
        NotificationServiceImpl service = new NotificationServiceImpl(userClient, channelFactory);

        // Arrange a valid event + user so code reaches provider.send(...)
        PaymentStatusEvent evt = new PaymentStatusEvent();
        evt.setUserId("user-014");
        evt.setTransactionId("txn-014");
        evt.setStatus("SUCCESS");
        evt.setAmount(2500L);
        evt.setCurrency("INR");

        com.payment.notificationservice.model.UserResponse.UserData u =
                new com.payment.notificationservice.model.UserResponse.UserData();
        u.setUserId("user-014");
        u.setName("Archit");
        u.setEmail("archit@example.com");

        when(userClient.getUserById("user-014")).thenReturn(u);
        when(channelFactory.get("EMAIL")).thenReturn(emailChannel);
        // Force the provider to throw to trigger the catch block
        doThrow(new RuntimeException("SMTP down")).when(emailChannel)
                .send(anyString(), anyString(), anyString(), anyMap());

        // Act: should not propagate the exception (only logs the error)
        assertDoesNotThrow(() -> service.notifyUser(evt));

        // Verify interactions up to the failure point
        verify(userClient).getUserById("user-014");
        verify(channelFactory).get("EMAIL");
        verify(emailChannel).send(anyString(), anyString(), anyString(), anyMap());
        verifyNoMoreInteractions(userClient, channelFactory, emailChannel);
    }
}
