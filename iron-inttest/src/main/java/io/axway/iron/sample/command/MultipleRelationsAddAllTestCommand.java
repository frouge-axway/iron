package io.axway.iron.sample.command;

import java.util.*;
import io.axway.iron.Command;
import io.axway.iron.ReadWriteTransaction;
import io.axway.iron.sample.model.Company;
import io.axway.iron.sample.model.Person;

public interface MultipleRelationsAddAllTestCommand extends Command<Void> {

    String personId();

    @Override
    default Void execute(ReadWriteTransaction tx) {
        Person person = tx.select(Person.class).where(Person::id).equalsTo(personId());

        Company currentCompany = person.worksAt();
        Company google = tx.select(Company.class).where(Company::name).equalsTo("Google");
        Company microsoft = tx.select(Company.class).where(Company::name).equalsTo("Microsoft");
        Company oracle = tx.select(Company.class).where(Company::name).equalsTo("Oracle");

        tx.update(person).
                onCollection(Person::previousCompanies).
                addAll(List.of(oracle, microsoft)).
                addAll(List.of(currentCompany, google)).
                done();

        return null;
    }
}

