package games.strategy.engine.pbem;

import java.io.File;

import games.strategy.engine.framework.startup.ui.editors.IBean;

/**
 * An interface for classes that can post a turn summary, the summary may also include a save game if the
 * implementing class supports this
 */
public interface IForumPoster extends IBean {
  /**
   * Called when the turn summary should be posted
   *
   * @param summary
   *        the forum summary
   * @param subject
   *        the forum subject
   * @return true if the post was successful
   */
  public boolean postTurnSummary(String summary, final String subject);

  /**
   * Get the reference to the posted turn summary
   *
   * @return the reference string, often a URL
   */
  public String getTurnSummaryRef();

  /**
   * Should the summary include the save game. When this is true the client must call addSaveGame prior to calling
   * postSummary
   *
   * @return true if the save game should be included in the summary
   */
  public boolean getIncludeSaveGame();

  /**
   * Configure if the save game should be included in the summary post
   *
   * @param include
   *        true if the save game should be included
   */
  public void setIncludeSaveGame(boolean include);

  /**
   * Should we also post at the end of combat move
   *
   * @return true if the save game should be included in the summary
   */
  public boolean getAlsoPostAfterCombatMove();

  /**
   * Configure if we should also post at the end of combat move
   *
   * @param include
   *        true if the save game should be included
   */
  public void setAlsoPostAfterCombatMove(boolean postAlso);

  /**
   * Called to add the save game to the summary, this should only be called if getIncludeSaveGame returns true
   *
   * @param saveGame
   *        the save game file
   * @param fileName
   */
  void addSaveGame(File saveGame, String fileName);

  /**
   * Create a clone of this object
   *
   * @return the clone
   */
  IForumPoster doClone();

  /**
   * Returns true if this forum poster supports attaching save games
   *
   * @return true if save games are supported
   */
  public boolean supportsSaveGame();

  /**
   * Can you view the forum post with this poster
   */
  public boolean getCanViewPosted();

  /**
   * Get the user name to login with
   */
  @Override
  public String getDisplayName();

  /**
   * Get the forum id
   *
   * @return the forum id
   */
  public String getTopicId();

  /**
   * get the user name to login
   *
   * @return the user name
   */
  public String getUsername();

  /**
   * Get the password to login
   *
   * @return the password
   */
  public String getPassword();

  /**
   * Set the forum id
   *
   * @param forumId
   *        the new forum id
   */
  public void setTopicId(String forumId);

  /**
   * Set the user name
   *
   * @param username
   *        the new user name
   */
  public void setUsername(String username);

  /**
   * Set the password
   *
   * @param password
   *        the new password
   */
  public void setPassword(String password);

  /**
   * Opens a browser and go to the forum post, identified by the forumId
   */
  public void viewPosted();

  /**
   * Remove any sensitive information, like passwords before this object is saved in as a game properties
   */
  void clearSensitiveInfo();

  /**
   * Each poster provides a message that is displayed on the progress bar when testing the poster
   *
   * @return the progress bar message
   */
  String getTestMessage();
}
