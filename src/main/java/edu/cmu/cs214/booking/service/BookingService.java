package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                int position = store.waitlistForRoom(room).size() + 1;
                int seq = nextWaitlistSeq++;
                store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
                return new BookingResult.Waitlisted(position);
            }
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }


    /**
     * Reports whether {@code room} is free over {@code interval}, so callers can
     * check availability before attempting to book.
     */
    public boolean isAvailable(Room room, TimeInterval interval) {
        for (Booking b : store.bookingsForRoom(room)) {
            if (b.interval().start() >= interval.start() && b.interval().start() < interval.end()) {
                return false;
            }
        }
        return true;
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }

    /** Returns the waitlist entries for {@code room}. */
    public List<WaitlistEntry> listWaitlist(Room room) {
        return store.waitlistForRoom(room);
    }

    /**
     * Cancels the confirmed booking with {@code bookingId}, freeing its slot. If
     * no booking has that id, does nothing and returns {@code false}. After a
     * successful cancellation, promotes at most one waiter — the earliest by
     * {@code seq} whose interval no longer overlaps any remaining confirmed
     * booking — into a confirmed booking. Returns {@code true} when a booking was
     * cancelled.
     */
    public boolean cancelBooking(String bookingId) {
        Optional<Booking> existing = store.findBooking(bookingId);
        if (existing.isEmpty()) {
            return false;
        }
        Room room = existing.get().room();
        store.removeBooking(bookingId);

        List<WaitlistEntry> waiting = store.waitlistForRoom(room).stream()
                .sorted(Comparator.comparingInt(WaitlistEntry::seq))
                .toList();
        List<Booking> remaining = store.bookingsForRoom(room);
        for (WaitlistEntry entry : waiting) {
            boolean conflicts = remaining.stream()
                    .anyMatch(b -> entry.interval().overlaps(b.interval()));
            if (!conflicts) {
                store.addBooking(new Booking("b" + nextBookingSeq++, room, entry.user(),
                        entry.interval()));
                store.removeWaitlistEntry(entry.id());
                break;
            }
        }
        return true;
    }
}
