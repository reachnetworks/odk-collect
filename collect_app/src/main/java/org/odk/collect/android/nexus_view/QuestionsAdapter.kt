package org.odk.collect.android.nexus_view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.recyclerview.widget.RecyclerView
import org.javarosa.core.model.data.IAnswerData
import org.odk.collect.android.R
import org.odk.collect.android.javarosawrapper.FormController
import org.odk.collect.android.listeners.WidgetValueChangedListener
import org.odk.collect.android.widgets.QuestionWidget
import timber.log.Timber

private const val TAG = "QuestionAdapterTag"

class QuestionsAdapter(
    private val dataSource: MutableList<out QuestionWidget>,
    private val formController: FormController,

    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionWidgetViewHolder>(),
    WidgetValueChangedListener {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): QuestionWidgetViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.nexus_recycler_item, parent, false)

        return QuestionWidgetViewHolder(view)
    }


    override fun getItemCount(): Int = dataSource.size


    override fun onBindViewHolder(holder: QuestionWidgetViewHolder, position: Int) {

        holder.linearLayoutContainer.removeAllViews()

        val questionWidget: QuestionWidget =
            dataSource[position]

        questionWidget.setValueChangedListener (this)

        if (holder.linearLayoutContainer.parent != null) {
            (holder.linearLayoutContainer.parent as ViewGroup).removeView(holder.itemView)

            holder.linearLayoutContainer.addView(questionWidget)
        }else{
            holder.linearLayoutContainer.addView(questionWidget)
        }


    }

    inner class QuestionWidgetViewHolder(mView: View) : RecyclerView.ViewHolder(mView) {
        val linearLayoutContainer: LinearLayout =
            mView.findViewById(R.id.custom_question_widget_container)

    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }



    fun getQuestionWidgetAt(position: Int) : QuestionWidget{
        return dataSource[position]
    }

    override fun widgetValueChanged(changedWidget: QuestionWidget?) {

        val notifiedAnswer = changedWidget?.answer

        val notifiedIndex = changedWidget?.formEntryPrompt?.index

        formController.saveAnswer(notifiedIndex, notifiedAnswer)

    }


}