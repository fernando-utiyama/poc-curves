package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository.base;

import java.util.List;

public interface InMemoryBaseRepository<T, ID> {

    void save(T data, ID id);

    T findById(ID id);

    List<T> getAll();
}
