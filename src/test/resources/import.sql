/* Clean out the database from the last testing run.
  Unfortunately setting spring.jpa.hibernate.ddl-auto to create or create-drop
  does not work; tables cannot be dropped because of running up against fk constraints.
  So, if I don't put in SQL here to clean out the previous run's data from the database,
  test will fail when run the second time.
  Note, ddl-auto still has to be set to create or create-drop, even though that does nothing,
  otherwise this file is ignore.
  Bottom line: We get a huge complain-y stack trace when running the test configuration, but things
  work.
  */
delete from VOTE;
delete from RESPONSE_OPTION;
delete from QUESTION;
delete from VOTER;
