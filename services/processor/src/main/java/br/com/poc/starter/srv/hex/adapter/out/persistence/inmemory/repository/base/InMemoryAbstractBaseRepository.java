package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository.base;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class InMemoryAbstractBaseRepository<T, ID> implements InMemoryBaseRepository<T, ID> {

    protected abstract ConcurrentHashMap<ID, T> getDatabase();

    @Override
    public void save(T data, ID id) { getDatabase().put(id, data); }

    @Override
    public T findById(ID id) { return getDatabase().get(id); }

    @Override
    public List<T> getAll() { return List.copyOf(getDatabase().values()); }
}
