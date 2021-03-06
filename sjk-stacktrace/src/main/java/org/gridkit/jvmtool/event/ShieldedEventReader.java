package org.gridkit.jvmtool.event;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This reader adds special handling for {@link ErrorEvent} and
 * enforces class type restriction on stream events.
 *
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class ShieldedEventReader<T extends Event> implements EventReader<T> {

    private static final ErrorHandler THROW_HANDLER = new ErrorHandler() {

        @Override
        public void onException(Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            else {
                throw new RuntimeException(e);
            }
        }
    };

    private final EventReader<Event> nested;
    private final Class<T> classFilter;
    private final ErrorHandler errorHandler;
    private T nextEvent;

    public ShieldedEventReader(EventReader<Event> nested, Class<T> classFilter) {
        this(nested, classFilter, THROW_HANDLER);
    }

    public ShieldedEventReader(EventReader<Event> nested, Class<T> classFilter, ErrorHandler errorHandler) {
        this.nested = nested;
        this.classFilter = classFilter;
        this.errorHandler = errorHandler;
    }

    @Override
    public <M extends Event> EventReader<M> morph(EventMorpher<T, M> morpher) {
        return MorphingEventReader.morph(this, morpher);
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }

    private void seekNext() {
        try {
            while(nested.hasNext()) {
                Event e = nested.next();
                if (e instanceof SimpleErrorEvent) {
                    // forward to error handler
                    errorHandler.onException(((SimpleErrorEvent) e).exception());
                }
                if (classFilter.isInstance(e)) {
                    nextEvent = classFilter.cast(e);
                    break;
                }
            }
        }
        catch(Exception e) {
            errorHandler.onException(e);
            if (classFilter.isAssignableFrom(SimpleErrorEvent.class)) {
                nextEvent = classFilter.cast(new SimpleErrorEvent(e));
            }
            else {
                seekNext();
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (nextEvent == null) {
            seekNext();
        }
        return nextEvent != null;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T event = nextEvent;
        nextEvent = null;
        return event;
    }

    @Override
    public T peekNext() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T event = nextEvent;
        return event;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
        nested.dispose();
    }
}
